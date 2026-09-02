package dev.specra.api.core.storage;

import dev.specra.api.config.SpecraProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Artifacts on the local filesystem, with genuinely signed URLs.
 *
 * <p>The default, and deliberately not a stub. A development machine should exercise the same code
 * path production does: the API stores bytes outside the database, hands the browser a link it
 * fetches directly, and that link expires. Swapping in S3 later is a second implementation of
 * {@link ObjectStore}, not a change to anything above it.
 *
 * <p>**The signature is real.** {@code expires} and {@code sig} are HMAC-SHA256 over the key and
 * the expiry, so a link cannot be edited into a different key or a later deadline, and cannot be
 * forged without the server's secret. Without that, "signed URL" would mean "any URL", and one
 * tenant could read another's trace by typing a path — evidence is exactly the kind of data where a
 * guessable URL is the whole vulnerability.
 */
@Component
@ConditionalOnMissingBean(name = "s3ObjectStore")
public class FilesystemObjectStore implements ObjectStore {

  public static final String KIND = "filesystem";

  private static final String ALGORITHM = "HmacSHA256";

  private final Path root;
  private final SecretKeySpec signingKey;

  public FilesystemObjectStore(SpecraProperties properties) {
    this.root = Path.of(properties.storage().dir()).toAbsolutePath().normalize();
    this.signingKey = signingKey(properties);
  }

  @Override
  public String kind() {
    return KIND;
  }

  @Override
  public void put(String key, Path file, String contentType) {
    Path target = resolve(key);
    try {
      Files.createDirectories(target.getParent());
      Files.copy(file, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot store artifact " + key, e);
    }
  }

  @Override
  public Optional<InputStream> open(String key) {
    Path file = resolve(key);
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    try {
      return Optional.of(Files.newInputStream(file));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read artifact " + key, e);
    }
  }

  @Override
  public Optional<String> url(String key, Duration ttl) {
    long expires = Instant.now().plus(ttl).getEpochSecond();
    String signature = sign(key, expires);
    // The key travels as a query parameter, not a path segment. A storage key contains slashes
    // (`runs/{run}/{item}/trace.zip`), and Tomcat rejects `%2F` inside a path with a 400 before
    // any handler sees it — which made every artifact link, valid or not, undownloadable.
    // Found by requesting one against a running server; no unit test would have shown it.
    return Optional.of(
        "/api/v1/artifacts?key="
            + java.net.URLEncoder.encode(key, StandardCharsets.UTF_8)
            + "&expires="
            + expires
            + "&sig="
            + signature);
  }

  @Override
  public void delete(String key) {
    try {
      Files.deleteIfExists(resolve(key));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot delete artifact " + key, e);
    }
  }

  /**
   * Whether this link was minted by us and has not expired.
   *
   * <p>The expiry is checked before the signature is compared, so an expired link is rejected
   * without spending time on cryptography; and the comparison itself is constant-time, because a
   * fast "wrong at byte three" answer is how a signature gets guessed a byte at a time.
   */
  public boolean isSignatureValid(String key, long expires, String signature) {
    if (Instant.now().getEpochSecond() > expires) {
      return false;
    }
    return java.security.MessageDigest.isEqual(
        sign(key, expires).getBytes(StandardCharsets.UTF_8),
        signature.getBytes(StandardCharsets.UTF_8));
  }

  private String sign(String key, long expires) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(signingKey);
      byte[] digest = mac.doFinal((key + "\n" + expires).getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException("Cannot sign an artifact URL", e);
    }
  }

  /**
   * The JWT secret, reused rather than a second one to configure.
   *
   * <p>It is already mandatory, already long enough, and already the thing that must not leak; a
   * separate optional secret would be one more variable to forget, and forgetting it would mean
   * unsigned links rather than a startup failure.
   */
  private static SecretKeySpec signingKey(SpecraProperties properties) {
    return new SecretKeySpec(
        properties.security().jwt().secret().getBytes(StandardCharsets.UTF_8), ALGORITHM);
  }

  /**
   * Resolves a key under the root, refusing anything that climbs out of it.
   *
   * <p>Keys are built by this application today, but they travel through a URL on the way back, and
   * a check that only holds while nobody edits the request is not a check.
   */
  private Path resolve(String key) {
    Path resolved = root.resolve(key).normalize();
    if (!resolved.startsWith(root)) {
      throw new IllegalArgumentException("An artifact key must stay under the storage root");
    }
    return resolved;
  }
}
