package dev.specra.api.feature.analysis.service;

import java.util.List;

/**
 * Everything the model is allowed to see about one failure.
 *
 * <p>A record rather than a free-form map so the contents are auditable at a glance — this is the
 * type invariant 6 is enforced on. What is *absent* is deliberate: no environment values, secret or
 * otherwise, and no credentials. A parameter appears by name only, exactly as it does in the IR, so
 * a prompt can say "the run supplied QA_PASSWORD" without ever saying what it was.
 *
 * <p>There is no DOM snapshot yet — inspection lands with M8. Until then locator analysis reasons
 * from the error message and the spec, and says so rather than inventing an element it never saw
 * (invariant 5: never guess a locator when a snapshot exists; the honest form of that rule when
 * none exists is to admit the uncertainty).
 *
 * @param errorType the deterministic pre-classification the report already made (LOCATOR_TIMEOUT,
 *     ASSERTION, …) — cheap, reliable, and worth handing over rather than asking a model to
 *     re-derive
 * @param specExcerpt the generated source around the failure, not the whole file
 * @param manualStep what the human originally wrote for the step that failed, which is what makes
 *     "the application legitimately changed" distinguishable from "the locator drifted"
 */
public record FailureEvidence(
    String testCaseReference,
    String testCaseTitle,
    String specPath,
    String browser,
    String errorMessage,
    String errorType,
    String failedStepId,
    String manualStep,
    String modelStep,
    String specExcerpt,
    List<String> availableArtifacts) {}
