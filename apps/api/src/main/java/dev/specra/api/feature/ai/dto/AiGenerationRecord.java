package dev.specra.api.feature.ai.dto;

import dev.specra.api.feature.ai.domain.AiGenerationKind;
import dev.specra.api.feature.ai.domain.AiGenerationStatus;
import java.util.UUID;

/**
 * What a pipeline step hands over to be written down: everything an auditor later reads off the
 * row. The decision fields are not here because nobody has decided anything yet.
 *
 * @param rationale the model's questions or the validator's objections when it failed; the
 *     proposal's own explanation when it did not
 */
public record AiGenerationRecord(
    UUID workspaceId,
    UUID projectId,
    AiGenerationKind kind,
    AiGenerationStatus status,
    String subjectType,
    UUID subjectId,
    UUID resultId,
    String inputChecksum,
    String provider,
    String model,
    Integer promptTokens,
    Integer completionTokens,
    Integer latencyMs,
    String rationale) {}
