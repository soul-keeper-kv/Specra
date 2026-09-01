package dev.specra.api.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
    @DefaultValue Security security) {

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
  public record Security(@DefaultValue("") String encryptionKey) {}
}
