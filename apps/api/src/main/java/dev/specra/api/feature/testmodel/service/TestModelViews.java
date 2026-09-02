package dev.specra.api.feature.testmodel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.specra.api.core.testmodel.Target;
import dev.specra.api.core.testmodel.TestModel;
import dev.specra.api.core.testmodel.TestModelJson;
import dev.specra.api.core.testmodel.TestStep;
import dev.specra.api.feature.testcase.dto.TestCaseResponse;
import dev.specra.api.feature.testcase.dto.TestCaseStepResponse;
import dev.specra.api.feature.testmodel.domain.TestModelVersion;
import dev.specra.api.feature.testmodel.dto.PageReference;
import dev.specra.api.feature.testmodel.dto.SourceCoverage;
import dev.specra.api.feature.testmodel.dto.TestModelResponse;
import dev.specra.api.feature.testmodel.dto.TestModelVersionResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * From a stored row to what a reviewer looks at. Pure functions over the document: the pages it
 * names, and which manual step became which model steps — the two views the workspace lays side by
 * side.
 */
final class TestModelViews {

  /**
   * The manual step id convention shared with the prompt: {@code ts-} plus the 1-based position.
   */
  static final String SOURCE_PREFIX = "ts-";

  private TestModelViews() {}

  static String sourceStepId(int position) {
    return SOURCE_PREFIX + position;
  }

  static TestModelResponse toResponse(TestModelVersion row, TestCaseResponse testCase) {
    TestModel model = read(row);
    return new TestModelResponse(
        row.getId(),
        row.getTestCaseId(),
        row.getVersion(),
        row.getIrVersion(),
        model,
        row.getChecksum(),
        row.getCreatedAt(),
        pages(model),
        coverage(model, testCase.steps()));
  }

  static TestModelVersionResponse toVersion(TestModelVersion row) {
    return new TestModelVersionResponse(
        row.getId(),
        row.getVersion(),
        row.getIrVersion(),
        row.getChecksum(),
        row.getCreatedAt(),
        (int) read(row).allSteps().count());
  }

  static List<PageReference> pages(TestModel model) {
    Map<String, Set<String>> elements = new LinkedHashMap<>();
    Map<String, Set<String>> stepIds = new LinkedHashMap<>();
    model
        .allSteps()
        .forEach(
            step -> {
              collect(step.target(), step.id(), elements, stepIds);
              collect(step.to(), step.id(), elements, stepIds);
            });
    List<PageReference> pages = new ArrayList<>();
    elements.forEach(
        (page, names) ->
            pages.add(new PageReference(page, List.copyOf(names), List.copyOf(stepIds.get(page)))));
    return List.copyOf(pages);
  }

  private static void collect(
      Target target,
      String stepId,
      Map<String, Set<String>> elements,
      Map<String, Set<String>> steps) {
    if (target == null || !target.isPage()) {
      return;
    }
    Set<String> names = elements.computeIfAbsent(target.page(), k -> new LinkedHashSet<>());
    if (target.element() != null) {
      names.add(target.element());
    }
    steps.computeIfAbsent(target.page(), k -> new LinkedHashSet<>()).add(stepId);
  }

  static List<SourceCoverage> coverage(TestModel model, List<TestCaseStepResponse> manualSteps) {
    List<TestStep> steps = model.allSteps().toList();
    List<SourceCoverage> coverage = new ArrayList<>();
    for (TestCaseStepResponse manual : manualSteps) {
      String sourceId = sourceStepId(manual.position());
      List<String> derived =
          steps.stream()
              .filter(step -> step.sourceStepIds().contains(sourceId))
              .map(TestStep::id)
              .toList();
      coverage.add(new SourceCoverage(sourceId, manual.position(), derived));
    }
    return List.copyOf(coverage);
  }

  /** A stored document passed every layer on the way in, so failing to bind it now is a bug. */
  static TestModel read(TestModelVersion row) {
    try {
      return TestModelJson.read(row.getDocument());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          "Stored Test Model " + row.getId() + " no longer binds to the v1 record", e);
    }
  }

  static String sha256(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is mandatory in every JVM", e);
    }
  }
}
