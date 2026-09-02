package dev.specra.api.feature.codegen.dto;

import dev.specra.api.core.testmodel.TestModelDiff;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One step that moved.
 *
 * @param stepId the IR step id, which is also what a run reports as the failing step — so an impact
 *     and a failure can be read against one another
 */
public record StepDeltaResponse(
    @Schema(example = "s3") String stepId,
    TestModelDiff.StepChange change,
    String page,
    String description) {}
