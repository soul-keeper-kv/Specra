package dev.specra.api.core.security;

import dev.specra.api.config.SpecraProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Encrypts the secrets Specra stores — provider API keys today, environment secrets later — with
 * AES-GCM under one key from the environment ({@code SPECRA_ENCRYPTION_KEY}).
 *
 * <p>Product invariant 6 is the reason this class exists: no secret reaches the database in
 * plaintext. The key deliberately lives outside the database, so a dump of one is not a dump of
 * both; and the cipher can be <em>absent</em> — a development machine without the variable still
 * boots, features still work, and the one thing refused is storing a secret, with an error that
 * says exactly which variable to set.
 *
 * <p>The wire form is {@code base64(iv || ciphertext || tag)}; a fresh random IV each call, as GCM
 * requires.
 */
@Component
public class SecretsCipher {

  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public SecretsCipher(SpecraProperties properties) {
    this.key = parseKey(properties.security().encryptionKey());
  }

  /** False when no usable key is configured; callers turn that into an actionable error. */
  public boolean isEnabled() {
    return key != null;
  }

  public String encrypt(String plaintext) {
    requireEnabled();
    try {
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      byte[] out = new byte[iv.length + encrypted.length];
      System.arraycopy(iv, 0, out, 0, iv.length);
      System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
      return Base64.getEncoder().encodeToString(out);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("AES-GCM encryption failed", e);
    }
  }

  public String decrypt(String ciphertext) {
    requireEnabled();
    try {
      byte[] in = Base64.getDecoder().decode(ciphertext);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, Arrays.copyOf(in, IV_BYTES)));
      byte[] plain = cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(
          "Decryption failed — was SPECRA_ENCRYPTION_KEY changed after this secret was stored?", e);
    }
  }

  private void requireEnabled() {
    if (key == null) {
      throw new IllegalStateException(
          "SecretsCipher is disabled: set SPECRA_ENCRYPTION_KEY. Callers must check isEnabled()"
              + " and answer with a problem document instead of reaching this.");
    }
  }

  /**
   * Fails the boot on a key that is present but unusable — silently running with encryption off
   * because of a truncated paste is the one outcome worse than not configuring it at all.
   */
  private static SecretKeySpec parseKey(String encoded) {
    if (!StringUtils.hasText(encoded)) {
      return null;
    }
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(encoded.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("SPECRA_ENCRYPTION_KEY is not valid base64", e);
    }
    if (bytes.length != 16 && bytes.length != 24 && bytes.length != 32) {
      throw new IllegalStateException(
          "SPECRA_ENCRYPTION_KEY must decode to 16, 24 or 32 bytes; got " + bytes.length);
    }
    return new SecretKeySpec(bytes, "AES");
  }
}
