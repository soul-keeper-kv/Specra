package dev.specra.api.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
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
    @DefaultValue Cors cors, @DefaultValue Ai ai, @DefaultValue Logging logging) {

  /** Origins allowed to call {@code /api/**} from a browser. */
  public record Cors(
      @NotEmpty @DefaultValue("http://localhost:3000") List<String> allowedOrigins) {}

  public record Ai(
      @NotBlank @DefaultValue("You are Specra's assistant.") String systemPrompt,
      @DefaultValue ChatMemory chatMemory,
      @DefaultValue Rag rag) {

    /** How much of a conversation is replayed to the model on each turn. */
    public record ChatMemory(@Min(2) @Max(200) @DefaultValue("40") int maxMessages) {}

    /** Tokens per chunk when a document is split for embedding. */
    public record Rag(@Min(100) @Max(4000) @DefaultValue("800") int chunkSize) {}
  }

  public record Logging(@DefaultValue Access access) {

    public record Access(
        @DefaultValue("true") boolean enabled,
        @Min(1) @DefaultValue("1000") long slowRequestMillis) {}
  }
}
