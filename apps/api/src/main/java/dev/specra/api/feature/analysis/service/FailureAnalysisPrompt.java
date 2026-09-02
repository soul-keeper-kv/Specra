package dev.specra.api.feature.analysis.service;

import dev.specra.api.feature.analysis.domain.RootCause;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

/**
 * Role 5 of the pipeline (08-ai-pipeline.md) as text: read the evidence, name the cause, and say
 * what to do — including, when the evidence says so, that the application is broken.
 *
 * <p>The cause list is generated from the enum rather than typed here, so the prompt cannot drift
 * from what the column will accept.
 *
 * <p>The system prompt spends most of its length on the one instruction that matters: PRODUCT_BUG
 * is a real answer. A model asked to "analyse a failure" will reliably propose *something*, and a
 * tool that repairs every failure by loosening the assertion that caught it is worse than no tool
 * (invariant 7). So the distinguishing question is stated explicitly, and the honest refusal is
 * given a name.
 */
final class FailureAnalysisPrompt {

  private FailureAnalysisPrompt() {}

  static String system() {
    String causes =
        Arrays.stream(RootCause.values()).map(Enum::name).collect(Collectors.joining(", "));
    return """
    You are the failure analysis step of Specra, a tool that turns manual test cases into \
    automation. You read the evidence from one failed automated test and decide what actually \
    went wrong.

    Return exactly one JSON object and nothing else - no prose, no code fences:
    {"rootCause": one of [%s], "confidence": 0-100, "summary": "...", "rationale": "...", \
    "suggestion": "..." or null}

    The causes, and how to tell them apart:
    - LOCATOR_DRIFT: the element still exists and still does its job, but the way the test \
    finds it no longer matches - renamed, restructured, moved. The fix is the page object.
    - TIMING: the element or state arrives, but later than the test looked. The fix is to wait \
    for a state, never to add a fixed delay.
    - APPLICATION_CHANGED: the flow itself is different now - a step was added, removed or \
    reordered. The manual test case needs a human's review; the code is not the problem.
    - TEST_DATA: the environment is missing a value or holds a stale one. Say which name.
    - PRODUCT_BUG: the test is correct and the application is wrong.
    - UNKNOWN: the evidence does not support any of the above.

    PRODUCT_BUG and UNKNOWN are real answers, and choosing one is not a failure on your part. \
    The question that separates PRODUCT_BUG from every other cause is: "if a human performed \
    these steps by hand right now, would they see what the test expected?" If they would not, \
    the application regressed - say so, set "suggestion" to null, and do not describe any \
    change to the test. Never propose weakening, removing or loosening an assertion to make a \
    test pass. If the only way to make this test green is to expect less than it expects now, \
    the answer is PRODUCT_BUG.

    Use UNKNOWN when the evidence is genuinely insufficient. A confident-sounding guess is \
    worse than an admission, because a person will act on it.

    "confidence" is how strongly the evidence supports the cause, not how fluent your \
    explanation is. Below 50 means you are largely inferring.
    "summary" is one sentence a manual QA can read. "rationale" ties the conclusion to the \
    specific evidence you were given - quote the part you relied on. "suggestion" is what to \
    do, in words, and is null for PRODUCT_BUG and UNKNOWN.

    You are shown the error, the failing step and an excerpt of the generated code. You are \
    not shown a DOM snapshot, so do not state what the page contains as if you had seen it - \
    if a specific new selector would settle it, say that inspection is needed.
    """
        .formatted(causes);
  }

  static String user(FailureEvidence evidence) {
    StringBuilder text = new StringBuilder();
    text.append("Test case: ")
        .append(evidence.testCaseReference())
        .append(" - ")
        .append(evidence.testCaseTitle())
        .append("\nSpec file: ")
        .append(evidence.specPath())
        .append("\nBrowser: ")
        .append(evidence.browser())
        .append('\n');

    if (StringUtils.hasText(evidence.errorType())) {
      // The report's own deterministic reading. Handing it over is cheaper and more reliable
      // than asking the model to re-derive it from the message.
      text.append("\nError type (classified from the message): ")
          .append(evidence.errorType())
          .append('\n');
    }
    text.append("\nError:\n").append(orNone(evidence.errorMessage())).append('\n');

    if (StringUtils.hasText(evidence.failedStepId())) {
      text.append("\nFailed at model step: ").append(evidence.failedStepId()).append('\n');
    }
    if (StringUtils.hasText(evidence.manualStep())) {
      // What the human asked for, which is what makes "the application changed" separable from
      // "the locator drifted" — the test may simply no longer describe the product.
      text.append("The manual step it came from: ").append(evidence.manualStep()).append('\n');
    }
    if (StringUtils.hasText(evidence.modelStep())) {
      text.append("The modelled intent: ").append(evidence.modelStep()).append('\n');
    }
    if (StringUtils.hasText(evidence.specExcerpt())) {
      text.append("\nGenerated code around the failure:\n")
          .append(evidence.specExcerpt())
          .append('\n');
    }
    if (evidence.availableArtifacts() != null && !evidence.availableArtifacts().isEmpty()) {
      // Named, not attached: the model cannot open a trace, and a reader can. Saying what exists
      // lets the rationale point at it instead of pretending to have watched it.
      text.append("\nEvidence a human can open (you cannot): ")
          .append(String.join(", ", evidence.availableArtifacts()))
          .append('\n');
    }
    return text.toString();
  }

  private static String orNone(String value) {
    return StringUtils.hasText(value) ? value : "(the run recorded no error message)";
  }
}
