package dev.specra.api.feature.codegen.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A code proposal, waiting for a person.
 *
 * <p>It is an {@code AiGeneration} row in {@code PROPOSED} plus the files it would write. Nothing
 * has touched the repository yet, and nothing will until somebody applies it — that action is what
 * produces a commit.
 *
 * @param status PROPOSED, APPLIED or REJECTED
 * @param modelVersion which version of the case's IR this was projected from
 */
public record CodeGenerationResponse(
    UUID id,
    UUID testCaseId,
    String reference,
    String status,
    int modelVersion,
    String adapterVersion,
    List<GeneratedFileResponse> files,
    List<UnresolvedTargetResponse> unresolved,
    String commitSha,
    Instant createdAt,
    Instant decidedAt) {}
