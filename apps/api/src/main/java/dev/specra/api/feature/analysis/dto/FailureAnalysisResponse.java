package dev.specra.api.feature.analysis.dto;

import dev.specra.api.feature.analysis.domain.RootCause;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * What the evidence said, and what to do about it.
 *
 * @param confidence 0..100, shown rather than hidden — a guess presented as a finding is the
 *     failure mode this whole feature exists to avoid
 * @param repairable false for a product bug and for an unclassifiable failure. The API decides
 *     this, not the client: a UI that worked it out for itself would eventually disagree with the
 *     endpoint that enforces it.
 * @param suggestion what to do, in words. Null when the answer is "nothing, in the test" — that is
 *     invariant 7 expressed in the payload rather than in a comment.
 */
public record FailureAnalysisResponse(
    UUID id,
    UUID testRunItemId,
    UUID testCaseId,
    @Schema(example = "LOCATOR_DRIFT") RootCause rootCause,
    @Schema(example = "82") int confidence,
    String summary,
    String rationale,
    String suggestion,
    boolean repairable,
    Instant createdAt) {}
