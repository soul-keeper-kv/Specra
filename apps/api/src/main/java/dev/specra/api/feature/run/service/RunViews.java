package dev.specra.api.feature.run.service;

import dev.specra.api.feature.run.domain.ItemStatus;
import dev.specra.api.feature.run.domain.TestArtifact;
import dev.specra.api.feature.run.domain.TestRun;
import dev.specra.api.feature.run.domain.TestRunItem;
import dev.specra.api.feature.run.dto.ArtifactResponse;
import dev.specra.api.feature.run.dto.RunItemResponse;
import dev.specra.api.feature.run.dto.RunResponse;
import dev.specra.api.feature.run.dto.RunTotals;
import dev.specra.api.feature.testcase.service.TestCaseService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Entities into responses.
 *
 * <p>Hand-written rather than MapStruct because two things are computed rather than copied: the
 * totals, and the test case reference each item carries so a run list reads as {@code TC-104}
 * instead of a uuid the user has never seen.
 */
@Component
public class RunViews {

  private final TestCaseService testCases;

  public RunViews(TestCaseService testCases) {
    this.testCases = testCases;
  }

  /** For a list: the counts, without the per-item detail nobody reads at that zoom level. */
  public RunResponse toSummary(TestRun run) {
    return response(run, List.of());
  }

  public RunResponse toDetail(TestRun run) {
    Map<UUID, String> references = referencesOf(run);
    return response(run, run.getItems().stream().map(item -> toItem(item, references)).toList());
  }

  private RunResponse response(TestRun run, List<RunItemResponse> items) {
    return new RunResponse(
        run.getId(),
        run.getProjectId(),
        run.getReference(),
        run.getEnvironmentId(),
        run.getCommitSha(),
        run.getDirtyDiff() != null,
        run.getTrigger(),
        run.getStatus(),
        run.getQueuedAt(),
        run.getStartedAt(),
        run.getCompletedAt(),
        run.getErrorMessage(),
        totalsOf(run),
        items);
  }

  private RunItemResponse toItem(TestRunItem item, Map<UUID, String> references) {
    return new RunItemResponse(
        item.getId(),
        item.getTestCaseId(),
        references.get(item.getTestCaseId()),
        item.getSpecPath(),
        titleOf(item),
        item.getBrowser(),
        item.getStatus(),
        item.getDurationMs(),
        item.getFailedStepId(),
        item.getErrorMessage(),
        item.getErrorType(),
        item.getStartedAt(),
        item.getCompletedAt(),
        item.getArtifacts().stream().map(RunViews::toArtifact).toList());
  }

  private static ArtifactResponse toArtifact(TestArtifact artifact) {
    return new ArtifactResponse(
        artifact.getId(),
        artifact.getKind(),
        artifact.getContentType(),
        artifact.getSizeBytes(),
        artifact.getExpiresAt());
  }

  /**
   * The spec's file name, which is what a run detail shows beside the browser.
   *
   * <p>The engine's own test title would be better, but it only exists after the run; before then
   * the file name is what there is, and it is stable.
   */
  private static String titleOf(TestRunItem item) {
    String path = item.getSpecPath();
    int slash = path.lastIndexOf('/');
    return slash < 0 ? path : path.substring(slash + 1);
  }

  private static RunTotals totalsOf(TestRun run) {
    int passed = 0;
    int failed = 0;
    int errored = 0;
    int skipped = 0;
    for (TestRunItem item : run.getItems()) {
      if (item.getStatus() == ItemStatus.PASSED) passed++;
      else if (item.getStatus() == ItemStatus.FAILED) failed++;
      else if (item.getStatus() == ItemStatus.ERROR) errored++;
      else if (item.getStatus() == ItemStatus.SKIPPED) skipped++;
    }
    return new RunTotals(run.getItems().size(), passed, failed, errored, skipped);
  }

  /**
   * One lookup per distinct case rather than per item: a run over three browsers has three items
   * for every case, and asking three times for the same reference is three queries for one answer.
   */
  private Map<UUID, String> referencesOf(TestRun run) {
    Map<UUID, String> references = new HashMap<>();
    for (TestRunItem item : run.getItems()) {
      references.computeIfAbsent(item.getTestCaseId(), id -> testCases.get(id).reference());
    }
    return references;
  }
}
