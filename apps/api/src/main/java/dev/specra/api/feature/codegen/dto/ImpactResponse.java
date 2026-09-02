package dev.specra.api.feature.codegen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;

/**
 * What changed in the Test Model since the version currently in the repository.
 *
 * <p>08-ai-pipeline.md asks for "a minimal change proposal + rationale". The generation itself
 * stays a whole deterministic projection — invariant 3 — so this is the rationale: it says which
 * steps moved and which pages they touch, so a reviewer reading a file diff knows what they are
 * looking for rather than re-deriving it from the code.
 *
 * <p>Null on a first generation. There is no previous version to diff against, and an impact saying
 * "14 steps added" would be technically true and useless.
 *
 * @param unchanged how many steps were left alone, so "3 of 14 changed" is sayable
 * @param pages the pages the changed steps touch — where a locator problem would surface
 * @param minor true when the change is small enough that a large file diff means the projection,
 *     not the test case, is what moved
 */
public record ImpactResponse(
    List<StepDeltaResponse> steps,
    Set<String> pages,
    @Schema(example = "11") int unchanged,
    boolean minor) {}
