package dev.specra.api.feature.analysis.service;

import dev.specra.api.feature.analysis.domain.FailureAnalysis;

/**
 * The second half of role 5: the reading becomes a patch.
 *
 * <p>The cause is already decided — this prompt never re-litigates it, because a model asked both
 * "what is wrong" and "fix it" in one breath will reliably talk itself into whichever answer has a
 * fix attached. The classification happened, a person can see it, and the repair is downstream of
 * it.
 *
 * <p>Most of the instruction is about restraint. The model is given the whole file and asked for
 * the whole file back, which is the only way it can produce something that compiles — but a model
 * handed a file will tidy it, and a repair that also reformats forty lines is a diff nobody
 * reviews. So "change as little as possible" is stated as the primary constraint, and the assertion
 * rule is repeated here even though the analysis already applied it: this is the step that writes
 * code, and it is the last place the rule can be enforced by words.
 */
final class RepairPrompt {

  private RepairPrompt() {}

  static String system() {
    return """
    You are the repair step of Specra, a tool that turns manual test cases into automation. You \
    are given one automated test file that failed, a diagnosis of why, and the evidence. You \
    return the same file with the smallest change that fixes it.

    Return exactly one JSON object and nothing else - no prose, no code fences:
    {"contents": "the complete file after your change", "rationale": "what you changed and why"}

    "contents" is the WHOLE file, not a fragment and not a diff. It must be valid TypeScript \
    that compiles against the file's existing imports.

    Change as little as possible. Do not reformat, reorder, rename or tidy anything you were \
    not asked to fix. Do not add comments explaining the fix. Every line you touch beyond the \
    failure is a line a reviewer has to read and approve for no reason.

    Never weaken the test to make it pass. Specifically, you must not:
    - remove an assertion, or replace it with a weaker one;
    - widen an expected value to match what the application currently does;
    - add a try/catch, a conditional or an optional check that lets the failure through;
    - replace waiting for a state with a fixed sleep or an arbitrary timeout increase.
    If the only way to make this test pass is to expect less than it expects now, the diagnosis \
    was wrong and there is nothing here for you to fix: return the file completely unchanged \
    and say so in "rationale".

    Fix by cause:
    - The element moved: correct the selector so it finds the element the test means. Use a \
    role, a label or visible text where you can; those survive markup changes that a CSS path \
    does not.
    - A timing race: wait for the state the next step needs - the element being visible, \
    enabled, or the value having arrived. Never a fixed delay.
    - Test data: name the variable that is missing or stale in "rationale". Do not hardcode a \
    value into the test to work around it.
    - The application changed: adjust the steps to the flow that exists now, keeping every \
    assertion as strict as it was.
    """;
  }

  static String user(FailureAnalysis analysis, String specPath, String contents) {
    return """
    Diagnosis: %s (confidence %d%%)
    %s

    What to do: %s

    File: %s

    ```typescript
    %s
    ```
    """
        .formatted(
            analysis.getRootCause().name(),
            analysis.getConfidence(),
            analysis.getRationale() == null ? "" : analysis.getRationale(),
            analysis.getSuggestion() == null ? "(none given)" : analysis.getSuggestion(),
            specPath,
            contents);
  }
}
