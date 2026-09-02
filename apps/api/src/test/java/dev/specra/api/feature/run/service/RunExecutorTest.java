package dev.specra.api.feature.run.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import dev.specra.api.core.runner.RunnerClient;
import dev.specra.api.feature.environment.service.EnvironmentService;
import dev.specra.api.feature.run.domain.ItemStatus;
import dev.specra.api.feature.run.domain.RunStatus;
import dev.specra.api.feature.run.domain.TestRun;
import dev.specra.api.feature.run.domain.TestRunItem;
import dev.specra.api.feature.run.domain.TestRunRepository;
import dev.specra.api.support.TestProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * How a runner answer becomes a run's state.
 *
 * <p>The interesting cases are the ones where the two disagree with each other: a cell the report
 * says nothing about, and a run with both a failure and an error in it.
 */
@ExtendWith(MockitoExtension.class)
class RunExecutorTest {

  private static final UUID RUN = UUID.randomUUID();

  @Mock TestRunRepository runs;
  @Mock EnvironmentService environments;
  @Mock RunnerClient runner;

  RunExecutor executor;

  @BeforeEach
  void setUp() {
    // `self` is this instance: the transactional proxy is Spring's business, and a unit test
    // asserting on state does not need one.
    executor =
        new RunExecutor(
            runs, environments, runner, null, TestProperties.withContentKind("testcase"));
    lenient().when(runs.save(any(TestRun.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  private TestRun runWith(String... specs) {
    TestRun run = new TestRun();
    run.setWorkspaceId(UUID.randomUUID());
    run.setProjectId(UUID.randomUUID());
    run.setStatus(RunStatus.RUNNING);
    for (String spec : specs) {
      TestRunItem item = new TestRunItem();
      item.setTestCaseId(UUID.randomUUID());
      item.setSpecPath(spec);
      item.setBrowser("chromium");
      item.setStatus(ItemStatus.RUNNING);
      run.addItem(item);
    }
    return run;
  }

  private static Map<String, Object> reported(String spec, String status, String stepId) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("specPath", spec);
    item.put("browser", "chromium");
    item.put("status", status);
    item.put("durationMs", 1234);
    if (stepId != null) {
      item.put("failedStepId", stepId);
      item.put("errorMessage", "expected visible");
      item.put("errorType", "LOCATOR_TIMEOUT");
    }
    return item;
  }

  @Test
  void aFailureCarriesItsStepIdOntoTheItem() {
    TestRun run = runWith("tests/login.spec.ts");
    when(runs.findById(RUN)).thenReturn(Optional.of(run));

    executor.record(RUN, Map.of("items", List.of(reported("tests/login.spec.ts", "FAILED", "s3"))));

    TestRunItem item = run.getItems().get(0);
    assertThat(item.getStatus()).isEqualTo(ItemStatus.FAILED);
    assertThat(item.getFailedStepId()).isEqualTo("s3");
    assertThat(item.getErrorType()).isEqualTo("LOCATOR_TIMEOUT");
    assertThat(item.getDurationMs()).isEqualTo(1234);
    assertThat(run.getStatus()).isEqualTo(RunStatus.FAILED);
  }

  /**
   * "We never heard about this one" must not look like "it passed". The engine can stop before
   * reaching a cell, and a silently green result is the worst possible reading of that.
   */
  @Test
  void aCellTheReportSaysNothingAboutBecomesAnErrorNotAPass() {
    TestRun run = runWith("tests/login.spec.ts", "tests/checkout.spec.ts");
    when(runs.findById(RUN)).thenReturn(Optional.of(run));

    executor.record(RUN, Map.of("items", List.of(reported("tests/login.spec.ts", "PASSED", null))));

    TestRunItem unreported =
        run.getItems().stream()
            .filter(item -> item.getSpecPath().equals("tests/checkout.spec.ts"))
            .findFirst()
            .orElseThrow();
    assertThat(unreported.getStatus()).isEqualTo(ItemStatus.ERROR);
    assertThat(unreported.getErrorMessage()).contains("no result");
    assertThat(run.getStatus()).isEqualTo(RunStatus.ERROR);
  }

  /**
   * ERROR outranks FAILED. While something was broken, a failing assertion cannot be trusted to say
   * anything about the application — and only a trustworthy FAILED is worth analysing.
   */
  @Test
  void anErrorAnywhereMakesTheWholeRunAnError() {
    TestRun run = runWith("tests/a.spec.ts", "tests/b.spec.ts");
    when(runs.findById(RUN)).thenReturn(Optional.of(run));

    executor.record(
        RUN,
        Map.of(
            "items",
            List.of(
                reported("tests/a.spec.ts", "FAILED", "s1"),
                reported("tests/b.spec.ts", "ERROR", null))));

    assertThat(run.getStatus()).isEqualTo(RunStatus.ERROR);
  }

  @Test
  void everyCellPassingIsAPass() {
    TestRun run = runWith("tests/a.spec.ts");
    when(runs.findById(RUN)).thenReturn(Optional.of(run));

    executor.record(RUN, Map.of("items", List.of(reported("tests/a.spec.ts", "PASSED", null))));

    assertThat(run.getStatus()).isEqualTo(RunStatus.PASSED);
    assertThat(run.getCompletedAt()).isNotNull();
  }

  /** A cancelled run is over; a late answer from the runner must not resurrect it. */
  @Test
  void aResultArrivingAfterCancellationIsIgnored() {
    TestRun run = runWith("tests/a.spec.ts");
    run.setStatus(RunStatus.CANCELLED);
    when(runs.findById(RUN)).thenReturn(Optional.of(run));

    executor.record(RUN, Map.of("items", List.of(reported("tests/a.spec.ts", "PASSED", null))));

    assertThat(run.getStatus()).isEqualTo(RunStatus.CANCELLED);
  }

  /** Claiming is what makes cancel work without interrupting a thread. */
  @Test
  void aRunCancelledBeforePickupIsNeverClaimed() {
    TestRun run = runWith("tests/a.spec.ts");
    run.setStatus(RunStatus.CANCELLED);
    when(runs.findById(RUN)).thenReturn(Optional.of(run));

    assertThat(executor.markRunning(RUN)).isEmpty();
  }
}
