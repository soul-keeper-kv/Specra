package dev.specra.api.support;

import dev.specra.api.config.SpecraProperties;
import java.time.Duration;
import java.util.List;

/**
 * A fully populated {@link SpecraProperties} for unit tests.
 *
 * <p>It is a record tree with no builder, so every test that needs one has to spell out all five
 * branches — and adding a field to any of them used to break every one of those tests at once, none
 * of which cared about the new field. Building it here means that edit lands in one file.
 *
 * <p>Each factory names the one branch its callers actually vary; the rest are the shipped
 * defaults.
 */
public final class TestProperties {

  /**
   * Long enough for the HS256 floor and obviously not a real key. Tests sign and verify with it in
   * the same process, so its only requirement is that it parses.
   */
  public static final String JWT_SECRET = "test-only-jwt-secret-that-is-long-enough-for-hs256";

  private TestProperties() {}

  /** For tests about content-store resolution, which vary only the default kind. */
  public static SpecraProperties withContentKind(String defaultKind) {
    return build(defaultKind, health(true, Duration.ofSeconds(10), Duration.ofSeconds(10)));
  }

  /** For tests about secrets at rest, which vary only the cipher key; "" means disabled. */
  public static SpecraProperties withEncryptionKey(String base64Key) {
    SpecraProperties defaults = withContentKind("testcase");
    return new SpecraProperties(
        defaults.cors(),
        defaults.ai(),
        defaults.content(),
        defaults.logging(),
        security(base64Key, defaults.security().lockout()),
        git());
  }

  /** For tests about sign-in throttling, which vary only the lockout policy. */
  public static SpecraProperties withLockout(int maxAttempts, Duration duration) {
    SpecraProperties defaults = withContentKind("testcase");
    return new SpecraProperties(
        defaults.cors(),
        defaults.ai(),
        defaults.content(),
        defaults.logging(),
        security("", new SpecraProperties.Security.Lockout(maxAttempts, duration)),
        git());
  }

  /** For tests about the AI health probe, which vary only its timings. */
  public static SpecraProperties withAiHealth(SpecraProperties.Ai.Health health) {
    return build("note", health);
  }

  public static SpecraProperties.Ai.Health health(
      boolean enabled, Duration timeout, Duration minRefreshInterval) {
    return new SpecraProperties.Ai.Health(
        enabled, Duration.ofMinutes(5), Duration.ofSeconds(10), timeout, minRefreshInterval);
  }

  /** Working copies under target/ so a test run never writes outside the build directory. */
  private static SpecraProperties.Git git() {
    return new SpecraProperties.Git("target/test-repos", "Specra", "bot@specra.dev");
  }

  private static SpecraProperties.Security security(
      String encryptionKey, SpecraProperties.Security.Lockout lockout) {
    return new SpecraProperties.Security(
        encryptionKey,
        new SpecraProperties.Security.Jwt(JWT_SECRET, "specra-test", 15, 30),
        lockout,
        true);
  }

  private static SpecraProperties build(String defaultKind, SpecraProperties.Ai.Health health) {
    return new SpecraProperties(
        new SpecraProperties.Cors(List.of("http://localhost:3000")),
        new SpecraProperties.Ai(
            "prompt",
            new SpecraProperties.Ai.ChatMemory(40),
            new SpecraProperties.Ai.Rag(800),
            health),
        new SpecraProperties.Content(defaultKind),
        new SpecraProperties.Logging(new SpecraProperties.Logging.Access(true, 1000)),
        security("", new SpecraProperties.Security.Lockout(5, Duration.ofMinutes(15))),
        git());
  }
}
