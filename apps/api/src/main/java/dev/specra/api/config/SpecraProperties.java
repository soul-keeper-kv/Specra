package dev.specra.api.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Everything under the {@code specra.*} prefix, in one typed tree.
 *
 * <p>Bound at startup and validated there, so a misspelt property or an out-of-range value fails
 * the boot rather than surfacing as odd behaviour hours later. Vendor-specific AI settings stay
 * under {@code spring.ai.*} where Spring AI expects them — nothing here names a provider.
 */
@ConfigurationProperties(prefix = "specra")
@Validated
public record SpecraProperties(
    @DefaultValue Cors cors,
    @DefaultValue Ai ai,
    @DefaultValue Content content,
    @DefaultValue Logging logging,
    @DefaultValue Security security,
    @DefaultValue Git git,
    @DefaultValue Runner runner,
    @DefaultValue Storage storage) {

  /** Origins allowed to call {@code /api/**} from a browser. */
  public record Cors(
      @NotEmpty @DefaultValue("http://localhost:3000") List<String> allowedOrigins) {}

  public record Ai(
      @NotBlank @DefaultValue("You are Specra's assistant.") String systemPrompt,
      @DefaultValue ChatMemory chatMemory,
      @DefaultValue Rag rag,
      @DefaultValue Health health) {

    /** How much of a conversation is replayed to the model on each turn. */
    public record ChatMemory(@Min(2) @Max(200) @DefaultValue("40") int maxMessages) {}

    /** Tokens per chunk when a document is split for embedding. */
    public record Rag(@Min(100) @Max(4000) @DefaultValue("800") int chunkSize) {}

    /**
     * The periodic probe of the chat provider behind {@code GET /api/ai/health}.
     *
     * <p>Durations are written ISO-8601 ({@code PT5M}) rather than in the binder's shorter {@code
     * 5m} form, because {@code @Scheduled} reads {@code interval} straight from the environment and
     * parses only ISO-8601. Two formats for one value is a startup failure waiting for whoever
     * edits it next.
     *
     * <p>The floors are what stops a probe schedule from becoming traffic: five seconds is already
     * far more often than a provider's availability changes, and a paid provider bills every one.
     */
    public record Health(
        @DefaultValue("true") boolean enabled,
        @NotNull @DurationMin(seconds = 30) @DefaultValue("PT5M") Duration interval,
        @NotNull @DurationMin(seconds = 1) @DefaultValue("PT10S") Duration initialDelay,
        @NotNull @DurationMin(seconds = 1) @DefaultValue("PT10S") Duration timeout,
        @NotNull @DurationMin(seconds = 5) @DefaultValue("PT10S") Duration minRefreshInterval) {}
  }

  public record Logging(@DefaultValue Access access) {

    public record Access(
        @DefaultValue("true") boolean enabled,
        @Min(1) @DefaultValue("1000") long slowRequestMillis) {}
  }

  /**
   * Which {@code ContentStore} the AI tools act on when the caller does not name one.
   *
   * <p>Checked against the registered stores at startup, so a typo fails the boot instead of
   * surfacing as a puzzling 400 the first time someone asks the assistant a question.
   */
  public record Content(@NotBlank @DefaultValue("testcase") String defaultKind) {}

  /**
   * @param encryptionKey base64, decoding to a 16/24/32-byte AES key; what {@code SecretsCipher}
   *     encrypts stored secrets with. Optional: without it the application runs and only storing a
   *     secret is refused. Generate one with {@code openssl rand -base64 32}.
   */
  public record Security(
      @DefaultValue("") String encryptionKey,
      @DefaultValue Jwt jwt,
      @DefaultValue Lockout lockout,
      @DefaultValue("true") boolean registrationOpen) {

    /**
     * The access token this API signs and verifies itself.
     *
     * <p>{@code secret} has no default on purpose. HS256 with a guessable key is not authentication
     * — anyone who knows the string mints tokens for any account — so a deployment that forgets to
     * set {@code SPECRA_JWT_SECRET} must fail to start rather than come up insecure. The 32-byte
     * floor is what HS256 needs to be worth using; generate one with {@code openssl rand -base64
     * 48}.
     *
     * <p>The access lifetime is short because an access token is never checked against the
     * database: revoking a session takes effect at the next refresh, so the window is the lifetime.
     * Fifteen minutes keeps that window small without making refreshes constant.
     */
    public record Jwt(
        @NotBlank @Size(min = 32, message = "specra.security.jwt.secret must be at least 32 characters") String secret,
        @NotBlank @DefaultValue("specra") String issuer,
        @Min(1) @Max(1440) @DefaultValue("15") int accessMinutes,
        @Min(1) @Max(365) @DefaultValue("30") int refreshDays) {}

    /**
     * Throttles password guessing. The lock is on the account and it expires on its own: a
     * permanent lock turns a guessing attempt into a denial of service against the real user, and a
     * support ticket for every one.
     */
    public record Lockout(
        @Min(1) @Max(100) @DefaultValue("5") int maxAttempts,
        @NotNull @DurationMin(seconds = 1) @DefaultValue("PT15M") Duration duration) {}
  }

  /**
   * Working copies and commit identity for the git feature.
   *
   * @param reposDir where working copies live, one directory per (project, branch). A cache by
   *     contract (07-git.md): deletable at any time, re-cloned on demand, never pointed into by the
   *     database. Relative paths resolve against the API's working directory, which is why the
   *     default is absolute and outside the checkout: a working copy below Specra is one the user's
   *     own toolchain walks up out of, finding Specra's manifest instead of the repository's.
   * @param committerName who the committer is on every commit Specra publishes. The author is the
   *     signed-in user — attribution belongs to the person who approved the change — and the
   *     committer is the tool, which is exactly how {@code git rebase} and friends record it.
   * @param committerEmail pairs with {@code committerName}; also the author fallback when no user
   *     is signed in, which outside tests should never happen.
   */
  public record Git(
      @NotBlank @DefaultValue("data/repos") String reposDir,
      @NotBlank @DefaultValue("Specra") String committerName,
      @NotBlank @DefaultValue("bot@specra.dev") String committerEmail) {}

  /**
   * Where the toolchain plane lives, and how long to wait for it.
   *
   * <p>A codegen job is a pure function over data already in memory, so it answers in milliseconds;
   * {@code timeout} is generous enough for a cold start and short enough that a hung runner
   * surfaces as a reported state rather than a request nobody ever gets an answer to.
   *
   * @param runTimeout what a run gets instead. A suite takes minutes, so it is dispatched on its
   *     own executor and the 30-second ceiling above would kill every real one.
   * @param maxConcurrentPerWorkspace how many runs one workspace may have queued or running. Per
   *     workspace rather than global, so a tenant that queues fifty slows only itself.
   */
  public record Runner(
      @NotBlank @DefaultValue("http://127.0.0.1:8090") String baseUrl,
      @DefaultValue("PT30S") Duration timeout,
      @NotNull @DurationMin(minutes = 1) @DefaultValue("PT20M") Duration runTimeout,
      @Min(1) @Max(50) @DefaultValue("3") int maxConcurrentPerWorkspace) {}

  /**
   * Where run evidence is kept, and for how long.
   *
   * @param dir the filesystem store's root. Like the git working copies it is a cache of things
   *     that can be produced again by re-running, so it is safe to delete — but unlike them it is
   *     the *only* copy of what a particular run saw, which is why it has an expiry rather than
   *     being cleared on a whim.
   * @param urlTtl how long a signed artifact link stays valid. Minutes, not days: it is handed to a
   *     browser that is about to fetch it, and a link that outlives the page it was rendered on is
   *     a permanent handle to someone's test evidence.
   * @param retention how long artifacts are kept before a sweep may remove them. Traces are large
   *     and a QA team runs a lot of tests, so this is a cost dial with a real default.
   */
  public record Storage(
      @NotBlank @DefaultValue("data/artifacts") String dir,
      @NotNull @DurationMin(seconds = 30) @DefaultValue("PT15M") Duration urlTtl,
      @NotNull @DurationMin(days = 1) @DefaultValue("P30D") Duration retention) {}
}
