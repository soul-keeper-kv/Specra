package dev.specra.api.feature.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * What the last periodic probe of the chat provider found.
 *
 * <p>Carries codes, never sentences: {@code status} and {@code code} are stable identifiers the web
 * app translates, the same way it translates an RFC 9457 {@code code}. A reading older than {@code
 * nextCheckAt} means the scheduler has not caught up yet, not that the provider changed.
 */
@Schema(description = "Result of the periodic chat-provider probe, as of checkedAt")
public record AiHealth(
    AiHealthStatus status,
    @Schema(
            description = "Active chat provider, as spring.ai.model.chat names it",
            example = "ollama")
        String chatProvider,
    @Schema(
            description = "ErrorCode slug explaining a non-UP status; null when UP or UNKNOWN",
            example = "ai-provider-unavailable")
        String code,
    @Schema(description = "When the probe ran; null when none has run yet") Instant checkedAt,
    @Schema(
            description = "Round trip of the probe call; null when nothing was sent",
            example = "412")
        Long latencyMillis,
    @Schema(description = "When the scheduler will probe again; null when checks are disabled")
        Instant nextCheckAt,
    @Schema(description = "Probes in a row that did not come back UP", example = "0")
        int consecutiveFailures) {

  /** The reading before anything has been probed. */
  public static AiHealth unknown(String chatProvider) {
    return new AiHealth(AiHealthStatus.UNKNOWN, chatProvider, null, null, null, null, 0);
  }
}
