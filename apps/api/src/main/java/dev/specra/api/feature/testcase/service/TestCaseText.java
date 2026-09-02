package dev.specra.api.feature.testcase.service;

import dev.specra.api.feature.testcase.dto.TestCaseResponse;
import dev.specra.api.feature.testcase.dto.TestCaseStepResponse;
import org.springframework.util.StringUtils;

/**
 * The one textual rendering of a test case — what gets chunked, embedded and shown to the model.
 *
 * <p>The labels are English on purpose and are not translation keys: this text is data for
 * retrieval, not UI. Rendering it per-locale would embed every case twice and make the retrieved
 * chunks depend on who indexed them; the author's own words stay in whatever language they wrote.
 */
final class TestCaseText {

  private TestCaseText() {}

  static String compose(TestCaseResponse testCase) {
    StringBuilder text = new StringBuilder();
    text.append(testCase.reference()).append(" — ").append(testCase.title());
    appendSection(text, null, testCase.description());
    appendSection(text, "Preconditions", testCase.preconditions());
    if (testCase.steps() != null && !testCase.steps().isEmpty()) {
      text.append("\n\nSteps:");
      for (TestCaseStepResponse step : testCase.steps()) {
        text.append('\n').append(step.position()).append(". ").append(step.action());
        if (StringUtils.hasText(step.data())) {
          text.append(" [data: ").append(step.data()).append(']');
        }
        if (StringUtils.hasText(step.expected())) {
          text.append(" => ").append(step.expected());
        }
      }
    }
    appendSection(text, "Expected result", testCase.expectedResult());
    return text.toString();
  }

  private static void appendSection(StringBuilder text, String label, String value) {
    if (!StringUtils.hasText(value)) {
      return;
    }
    text.append("\n\n");
    if (label != null) {
      text.append(label).append(": ");
    }
    text.append(value.trim());
  }
}
