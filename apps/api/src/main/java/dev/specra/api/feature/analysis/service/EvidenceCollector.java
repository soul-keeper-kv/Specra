package dev.specra.api.feature.analysis.service;

import dev.specra.api.core.testmodel.TestModel;
import dev.specra.api.core.testmodel.TestStep;
import dev.specra.api.feature.git.service.GitService;
import dev.specra.api.feature.run.domain.TestRunItem;
import dev.specra.api.feature.testcase.dto.TestCaseResponse;
import dev.specra.api.feature.testcase.dto.TestCaseStepResponse;
import dev.specra.api.feature.testmodel.service.TestModelStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Assembles what a model may see about one failure, and nothing else.
 *
 * <p>Its real job is the chain the run only half records. The report gives a failed IR step id;
 * this walks that to the modelled intent and on to the manual step the human wrote, because those
 * three together are what separates "the locator drifted" from "the application legitimately
 * changed" — the error message alone cannot tell them apart.
 *
 * <p>Invariant 6 lives here too: environment values never enter the bundle, secret or otherwise.
 * The evidence is the error, the code and the intent — never the data the run was given.
 */
@Service
@Transactional(readOnly = true)
public class EvidenceCollector {

  private static final Logger log = LoggerFactory.getLogger(EvidenceCollector.class);

  /**
   * Lines of generated code either side of the failure.
   *
   * <p>An excerpt rather than the file: a spec is mostly setup that had nothing to do with this,
   * and a prompt that carries all of it buries the three lines that matter.
   */
  private static final int CONTEXT_LINES = 12;

  private final GitService git;
  private final TestModelStore models;

  public EvidenceCollector(GitService git, TestModelStore models) {
    this.git = git;
    this.models = models;
  }

  public FailureEvidence collect(TestRunItem item, TestCaseResponse testCase) {
    Optional<TestStep> modelStep = failedStep(testCase, item.getFailedStepId());

    return new FailureEvidence(
        testCase.reference(),
        testCase.title(),
        item.getSpecPath(),
        item.getBrowser(),
        item.getErrorMessage(),
        item.getErrorType(),
        item.getFailedStepId(),
        modelStep.map(step -> manualStepFor(testCase, step)).orElse(null),
        modelStep.map(EvidenceCollector::describe).orElse(null),
        excerpt(item),
        artifactKinds(item));
  }

  /** The IR step the run says failed, from the case's current model. */
  private Optional<TestStep> failedStep(TestCaseResponse testCase, String stepId) {
    if (!StringUtils.hasText(stepId)) {
      return Optional.empty();
    }
    TestModel model;
    try {
      model = models.current(testCase.id()).document();
    } catch (RuntimeException e) {
      // A case can be run and then have its model deleted or replaced. The failure is still
      // analysable from the error and the code — worse evidence, not no evidence.
      log.debug("No readable Test Model for {}: {}", testCase.reference(), e.toString());
      return Optional.empty();
    }

    List<TestStep> all = new ArrayList<>(model.setup());
    all.addAll(model.steps());
    all.addAll(model.teardown());
    return all.stream().filter(step -> stepId.equals(step.id())).findFirst();
  }

  /**
   * The manual step the failed model step traces back to.
   *
   * <p>The IR carries {@code sourceStepIds} as {@code ts-1 …}, which is the 1-based position of the
   * manual step — the same convention the modelling prompt writes them with.
   */
  private static String manualStepFor(TestCaseResponse testCase, TestStep step) {
    for (String source : step.sourceStepIds()) {
      int position = positionOf(source);
      if (position <= 0) {
        continue;
      }
      for (TestCaseStepResponse manual : testCase.steps()) {
        if (manual.position() == position) {
          String expected =
              StringUtils.hasText(manual.expected())
                  ? " (expected: " + manual.expected() + ")"
                  : "";
          return manual.action() + expected;
        }
      }
    }
    return null;
  }

  private static int positionOf(String sourceStepId) {
    if (sourceStepId == null || !sourceStepId.startsWith("ts-")) {
      return -1;
    }
    try {
      return Integer.parseInt(sourceStepId.substring(3));
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  /** The step's intent in one line — the action, what it acted on, and what it asserted. */
  private static String describe(TestStep step) {
    StringBuilder text = new StringBuilder(step.action().code());
    if (step.target() != null) {
      text.append(' ').append(step.target().page());
      if (StringUtils.hasText(step.target().element())) {
        text.append('.').append(step.target().element());
      }
    }
    if (step.assertion() != null) {
      text.append(" asserting ").append(step.assertion().condition().code());
      if (step.assertion().expected() != null) {
        text.append(" = ").append(step.assertion().expected());
      }
    }
    if (StringUtils.hasText(step.description())) {
      text.append(" — ").append(step.description());
    }
    return text.toString();
  }

  /**
   * The generated code around the failure.
   *
   * <p>Read from Git rather than from the proposal that produced it: the run executed a commit, and
   * a proposal applied since would show code that never ran. The step id is what locates it — the
   * adapter writes each IR step as {@code test.step("… [s3]", …)}, so the marker is in the source
   * rather than needing a line number the report does not give.
   */
  private String excerpt(TestRunItem item) {
    String source = git.readFileOrNull(item.getRun().getProjectId(), item.getSpecPath());
    if (source == null) {
      return null;
    }
    String[] lines = source.split("\n", -1);
    int anchor = anchorLine(lines, item.getFailedStepId());
    if (anchor < 0) {
      // No marker to centre on: the head of the file is still worth more than nothing, and it
      // carries the imports and the fixtures that say how the test is wired.
      return join(lines, 0, Math.min(lines.length, CONTEXT_LINES * 2));
    }
    return join(
        lines,
        Math.max(0, anchor - CONTEXT_LINES),
        Math.min(lines.length, anchor + CONTEXT_LINES + 1));
  }

  private static int anchorLine(String[] lines, String stepId) {
    if (!StringUtils.hasText(stepId)) {
      return -1;
    }
    String marker = "[" + stepId + "]";
    for (int index = 0; index < lines.length; index++) {
      if (lines[index].contains(marker)) {
        return index;
      }
    }
    return -1;
  }

  private static String join(String[] lines, int from, int to) {
    StringBuilder text = new StringBuilder();
    for (int index = from; index < to; index++) {
      // Numbered, so a rationale can point at a line rather than describe its neighbourhood.
      text.append(index + 1).append(": ").append(lines[index]).append('\n');
    }
    return text.toString();
  }

  /**
   * Which evidence a person could open, by kind.
   *
   * <p>Named, never attached: the model cannot watch a video or read a trace, and letting it imply
   * otherwise is how a rationale ends up describing something nobody recorded.
   */
  private static List<String> artifactKinds(TestRunItem item) {
    return item.getArtifacts().stream()
        .map(artifact -> artifact.getKind().name())
        .distinct()
        .toList();
  }
}
