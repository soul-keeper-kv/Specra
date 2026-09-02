package dev.specra.api.feature.testmodel.service;

import dev.specra.api.core.testmodel.AssertCondition;
import dev.specra.api.core.testmodel.SchemaViolation;
import dev.specra.api.core.testmodel.StepAction;
import dev.specra.api.feature.testcase.dto.TestCaseResponse;
import dev.specra.api.feature.testcase.dto.TestCaseStepResponse;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

/**
 * Roles 1 and 2 of the pipeline (08-ai-pipeline.md) as text: understand the case, and either model
 * it or say precisely what is missing.
 *
 * <p>The action and condition lists are generated from the enums rather than typed here, so the
 * prompt cannot fall behind the schema — the vocabulary test already guards the enums against it.
 * The manual steps are numbered {@code ts-1 …} because that is the traceability id the schema
 * demands on every model step; {@link TestModelViews#sourceStepId} is the same convention.
 */
final class TestModelPrompt {

  private TestModelPrompt() {}

  static String system() {
    String actions =
        Arrays.stream(StepAction.values()).map(StepAction::code).collect(Collectors.joining(", "));
    String conditions =
        Arrays.stream(AssertCondition.values())
            .map(AssertCondition::code)
            .collect(Collectors.joining(", "));
    return """
    You are the modelling step of Specra, a tool that turns manual test cases into automation. \
    You read one manual test case and produce a Test Model: a JSON document describing the \
    test's intent, with no execution engine anywhere in it.

    Return exactly one JSON object and nothing else - no prose, no code fences:
    {"ambiguities": [{"sourceStepId": "ts-2", "question": "..."}], "model": <Test Model or null>}

    Ambiguities. If a manual step cannot become an action from the vocabulary below without \
    guessing (which element, which page, what "works correctly" means, which data), do not \
    guess: add a specific question for that step and set "model" to null. Ask only what a \
    tester could answer by editing the test case. When nothing is ambiguous, "ambiguities" is [].

    Test Model shape (irVersion is always 1):
    {"irVersion": 1, "name": string, "description"?: string, "tags"?: [slug],
     "parameters"?: [{"name": lowerCamelCase, "type": "string"|"number"|"boolean", \
    "required"?: boolean, "secret"?: boolean, "description"?: string}],
     "setup"?: [step], "steps": [step], "teardown"?: [step]}
    step: {"id": "s1", "sourceStepIds": ["ts-1"], "derived"?: boolean, "description"?: string,
           "action": action, "target"?: {"page": "LoginPage", "element"?: "submitButton"},
           "to"?: target, "value"?: value, "assertion"?: assertion, "flow"?: name}
    value: {"kind": "literal", "value": string|number|boolean} \
    | {"kind": "param", "name": parameterName} | {"kind": "secret", "name": parameterName}
    assertion: {"condition": condition, "expected"?: string|number|boolean, "attribute"?: string}

    Actions (closed list): %s
    - navigate takes a page target only. fill, clear, select, check, uncheck, upload, click, \
    doubleClick, rightClick, hover and dragTo need a page and an element. fill, select, upload \
    and press carry a value. dragTo also names "to". assert and waitFor take a target and an \
    assertion. reload, goBack and goForward take nothing. useFlow names a flow and nothing else.
    Assertion conditions (closed list): %s
    - textEquals, textContains, valueEquals and urlMatches need a string "expected"; \
    countEquals an integer; attributeEquals needs "attribute" and "expected"; visible, hidden, \
    enabled, disabled and checked take nothing else.

    Rules:
    1. Every step names the manual steps it came from in "sourceStepIds" (ts-1, ts-2, ...). A \
    step no manual step asked for must carry "derived": true; use that only for something the \
    case plainly assumes, such as opening the page it starts on.
    2. Step ids are s1, s2, ... and unique.
    3. Never write a CSS or XPath selector and never write engine code (page.click, getByRole, \
    cy., driver, expect, await). Describe intent in plain words.
    4. Pages are UpperCamelCase ending in Page (LoginPage); elements are lowerCamelCase naming \
    their role (usernameInput, submitButton). No page has been inspected yet: these names are \
    proposals that a later inspection of the real application resolves.
    5. Test data the case names becomes a declared parameter read with kind "param". Passwords, \
    tokens, keys and anything a person would not paste into a chat become parameters with \
    "secret": true, read with kind "secret" - their value never appears in the document. \
    Small harmless fixed values may be literals.
    6. A manual step that checks something becomes an assert step, and the overall expected \
    result becomes the final assert. The test must assert at least once; if the case verifies \
    nothing at all, that is an ambiguity.
    7. Never wait by time. Waiting is waitFor with a condition.
    8. Write name, descriptions and questions in the language the test case is written in.
    """
        .formatted(actions, conditions);
  }

  static String user(TestCaseResponse testCase) {
    StringBuilder text = new StringBuilder();
    text.append("Test case ").append(testCase.reference()).append(": ").append(testCase.title());
    if (StringUtils.hasText(testCase.description())) {
      text.append("\nDescription: ").append(testCase.description().trim());
    }
    if (StringUtils.hasText(testCase.preconditions())) {
      text.append("\nPreconditions: ").append(testCase.preconditions().trim());
    }
    if (!testCase.tags().isEmpty()) {
      text.append("\nTags: ").append(String.join(", ", testCase.tags().stream().sorted().toList()));
    }
    text.append("\n\nManual steps:");
    for (TestCaseStepResponse step : testCase.steps()) {
      text.append('\n')
          .append(TestModelViews.sourceStepId(step.position()))
          .append(". Action: ")
          .append(step.action().trim());
      if (StringUtils.hasText(step.data())) {
        text.append("\n    Data: ").append(step.data().trim());
      }
      if (StringUtils.hasText(step.expected())) {
        text.append("\n    Expected: ").append(step.expected().trim());
      }
    }
    if (StringUtils.hasText(testCase.expectedResult())) {
      text.append("\n\nOverall expected result: ").append(testCase.expectedResult().trim());
    }
    return text.toString();
  }

  /**
   * The one repair round the pipeline allows: the objections, verbatim, and the same contract. The
   * previous answer is included so the model corrects it rather than starting over.
   */
  static String repair(String previousAnswer, List<SchemaViolation> violations) {
    StringBuilder text = new StringBuilder();
    text.append("Your previous answer was:\n").append(previousAnswer.trim());
    text.append("\n\nIt failed validation:");
    for (SchemaViolation violation : violations) {
      text.append("\n- ").append(violation.path()).append(": ").append(violation.message());
    }
    text.append(
        "\n\nReturn the corrected, complete JSON object only, under the same output contract."
            + " If a problem cannot be fixed without guessing, report it as an ambiguity instead.");
    return text.toString();
  }
}
