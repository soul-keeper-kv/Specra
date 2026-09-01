package dev.specra.api.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specra.api.support.TestProperties;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretsCipherTest {

  private static final String KEY_32 =
      Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

  @Test
  void roundTripsAndNeverStoresThePlaintext() {
    SecretsCipher cipher = new SecretsCipher(TestProperties.withEncryptionKey(KEY_32));

    String secret = "sk-ant-abc123-xyz";
    String stored = cipher.encrypt(secret);

    assertThat(stored).doesNotContain(secret).doesNotContain("sk-ant");
    assertThat(cipher.decrypt(stored)).isEqualTo(secret);
  }

  /** GCM demands a fresh IV per message; identical outputs would mean the IV is being reused. */
  @Test
  void encryptingTheSameValueTwiceProducesDifferentCiphertext() {
    SecretsCipher cipher = new SecretsCipher(TestProperties.withEncryptionKey(KEY_32));

    assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
  }

  @Test
  void isDisabledWithoutAKeyAndRefusesToEncrypt() {
    SecretsCipher cipher = new SecretsCipher(TestProperties.withEncryptionKey(""));

    assertThat(cipher.isEnabled()).isFalse();
    assertThatThrownBy(() -> cipher.encrypt("anything"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SPECRA_ENCRYPTION_KEY");
  }

  /** A truncated paste must fail the boot, not silently run with a weaker key. */
  @Test
  void aKeyOfTheWrongLengthFailsConstruction() {
    String shortKey = Base64.getEncoder().encodeToString("too-short".getBytes());

    assertThatThrownBy(() -> new SecretsCipher(TestProperties.withEncryptionKey(shortKey)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("16, 24 or 32");
    assertThatThrownBy(() -> new SecretsCipher(TestProperties.withEncryptionKey("!not base64!")))
        .isInstanceOf(IllegalStateException.class);
  }
}
