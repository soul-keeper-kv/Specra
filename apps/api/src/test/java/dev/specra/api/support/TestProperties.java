package dev.specra.api.support;

import dev.specra.api.config.SpecraProperties;
import java.time.Duration;
import java.util.List;

/**
 * A fully populated {@link SpecraProperties} for unit tests.
 *
 * <p>It is a record tree with no builder, so every test that needs one has to spell out all four
 * branches — and adding a field to any of them used to break every one of those tests at once, none
 * of which cared about the new field. Building it here means that edit lands in one file.
 *
 * <p>Each factory names the one branch its callers actually vary; the rest are the shipped
 * defaults.
 */
public final class TestProperties {

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
        new SpecraProperties.Security(base64Key));
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
        new SpecraProperties.Security(""));
  }
}
