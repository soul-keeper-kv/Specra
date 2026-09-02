package dev.specra.api.feature.run.dto;

import dev.specra.api.feature.run.domain.ItemStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param failedStepId an IR step id, so the UI can highlight the manual step and the generated line
 *     together; null when the failure was not inside a step
 */
public record RunItemResponse(
    UUID id,
    UUID testCaseId,
    String testCaseReference,
    String specPath,
    String title,
    String browser,
    ItemStatus status,
    Integer durationMs,
    String failedStepId,
    String errorMessage,
    String errorType,
    Instant startedAt,
    Instant completedAt,
    List<ArtifactResponse> artifacts) {}
