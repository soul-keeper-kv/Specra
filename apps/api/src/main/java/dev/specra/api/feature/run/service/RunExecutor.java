package dev.specra.api.feature.run.service;

import dev.specra.api.config.SpecraProperties;
import dev.specra.api.core.runner.RunnerClient;
import dev.specra.api.feature.environment.service.EnvironmentService;
import dev.specra.api.feature.run.domain.ItemStatus;
import dev.specra.api.feature.run.domain.RunStatus;
import dev.specra.api.feature.run.domain.TestRun;
import dev.specra.api.feature.run.domain.TestRunItem;
import dev.specra.api.feature.run.domain.TestRunRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Where a run actually happens: off the request thread, on a pool of its own.
 *
 * <p>Its own executor rather than the scheduler's, for the reason {@code SchedulingConfig} spells
 * out — a run blocks for minutes, and one hung suite must not stop every other timed job in the
 * application. The pool is deliberately small: each task drives a browser through the runner, and
 * oversubscribing turns a slow run into several slower ones.
 *
 * <p>Progress is written to the database as it goes, so a user who refreshes sees the run move.
 * That is also why each transition is its own short transaction: holding one open for the length of
 * a suite would pin a connection and make the run invisible until it ended.
 */
@Component
public class RunExecutor {

  private static final Logger log = LoggerFactory.getLogger(RunExecutor.class);

  private final TestRunRepository runs;
  private final EnvironmentService environments;
  private final RunnerClient runner;
  private final RunExecutor self;
  private final ExecutorService pool;
  private final long runTimeoutMs;

  public RunExecutor(
      TestRunRepository runs,
      EnvironmentService environments,
      RunnerClient runner,
      @org.springframework.context.annotation.Lazy RunExecutor self,
      SpecraProperties properties) {
    this.runs = runs;
    this.environments = environments;
    this.runner = runner;
    // Through the proxy, so the @Transactional boundaries below actually apply: a self-call on
    // `this` would bypass them and run the writes outside a transaction.
    this.self = self;
    this.runTimeoutMs = properties.runner().runTimeout().toMillis();
    this.pool = Executors.newFixedThreadPool(2, named());
  }

  /** Hands the run to the pool. Returns at once; the caller's transaction is already done. */
  public void enqueue(UUID runId, String projectDir) {
    pool.submit(() -> dispatch(runId, projectDir));
  }

  private void dispatch(UUID runId, String projectDir) {
    try {
      Optional<TestRun> claimed = self.markRunning(runId);
      if (claimed.isEmpty()) {
        // Cancelled between request and pickup. Nothing to do, and not an error.
        return;
      }
      TestRun run = claimed.get();

      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("projectDir", projectDir);
      payload.put("baseUrl", environments.baseUrlOf(run.getEnvironmentId()));
      payload.put("browsers", browsersOf(run));
      payload.put("specs", specsOf(run));
      // Decrypted here and nowhere else, for the length of this call.
      payload.put("variables", environments.resolveForDispatch(run.getEnvironmentId()));
      payload.put("timeoutMs", runTimeoutMs);

      RunnerClient.RunnerJobResult job = runner.run("execute", payload);
      if (!job.ok()) {
        self.finishWithError(runId, job.message());
        return;
      }
      self.record(runId, job.result());
    } catch (RuntimeException e) {
      // Never let a run sit in RUNNING for ever because something threw on the way.
      log.warn("Run {} could not complete", runId, e);
      self.finishWithError(runId, e.getMessage());
    }
  }

  /**
   * Claims the run, unless somebody cancelled it first.
   *
   * <p>Empty means "do not proceed", which is what makes cancel work without interrupting a thread:
   * the executor simply never starts.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<TestRun> markRunning(UUID runId) {
    Optional<TestRun> found = runs.findById(runId);
    if (found.isEmpty() || found.get().getStatus() != RunStatus.QUEUED) {
      return Optional.empty();
    }
    TestRun run = found.get();
    run.setStatus(RunStatus.RUNNING);
    run.setStartedAt(Instant.now());
    run.getItems().forEach(item -> item.setStatus(ItemStatus.RUNNING));
    return Optional.of(runs.save(run));
  }

  /**
   * Writes the runner's answer onto the run.
   *
   * <p>Results are matched back by (spec path, browser), which is the identity of a matrix cell. A
   * cell the report says nothing about — the engine never reached it — is left as ERROR rather than
   * quietly PASSED, because "we do not know" and "it worked" must not look the same.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(UUID runId, Map<String, Object> result) {
    TestRun run = runs.findById(runId).orElse(null);
    if (run == null || run.getStatus() == RunStatus.CANCELLED) {
      return;
    }

    Map<String, Map<String, Object>> byCell = new LinkedHashMap<>();
    for (Map<String, Object> item : itemsOf(result)) {
      byCell.put(
          cellKey(String.valueOf(item.get("specPath")), String.valueOf(item.get("browser"))), item);
    }

    for (TestRunItem item : run.getItems()) {
      Map<String, Object> reported = byCell.get(cellKey(item.getSpecPath(), item.getBrowser()));
      if (reported == null) {
        item.setStatus(ItemStatus.ERROR);
        item.setErrorMessage("The runner reported no result for this test.");
        item.setCompletedAt(Instant.now());
        continue;
      }
      item.setStatus(statusOf(reported.get("status")));
      item.setDurationMs(intOf(reported.get("durationMs")));
      item.setFailedStepId(stringOf(reported.get("failedStepId")));
      item.setErrorMessage(stringOf(reported.get("errorMessage")));
      item.setErrorType(stringOf(reported.get("errorType")));
      item.setCompletedAt(Instant.now());
    }

    run.setStatus(rollUp(run));
    run.setErrorMessage(stringOf(result.get("errorMessage")));
    run.setCompletedAt(Instant.now());
    runs.save(run);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void finishWithError(UUID runId, String message) {
    runs.findById(runId)
        .filter(run -> !run.getStatus().isTerminal())
        .ifPresent(
            run -> {
              run.setStatus(RunStatus.ERROR);
              run.setErrorMessage(message);
              run.setCompletedAt(Instant.now());
              run.getItems().stream()
                  .filter(item -> item.getStatus() == ItemStatus.RUNNING)
                  .forEach(item -> item.setStatus(ItemStatus.ERROR));
              runs.save(run);
            });
  }

  /**
   * The run's status from its cells.
   *
   * <p>ERROR wins over FAILED: while something was broken, the failures cannot be trusted to mean
   * anything about the application, and only a trustworthy FAILED is worth analysing.
   */
  private static RunStatus rollUp(TestRun run) {
    boolean errored = run.getItems().stream().anyMatch(i -> i.getStatus() == ItemStatus.ERROR);
    if (errored) return RunStatus.ERROR;
    boolean failed = run.getItems().stream().anyMatch(i -> i.getStatus() == ItemStatus.FAILED);
    return failed ? RunStatus.FAILED : RunStatus.PASSED;
  }

  /** Distinct, because a matrix of three browsers is three cells per spec, not three specs. */
  private static List<String> specsOf(TestRun run) {
    Set<String> specs = new LinkedHashSet<>();
    run.getItems().forEach(item -> specs.add(item.getSpecPath()));
    return new ArrayList<>(specs);
  }

  private static List<String> browsersOf(TestRun run) {
    Set<String> browsers = new LinkedHashSet<>();
    run.getItems().forEach(item -> browsers.add(item.getBrowser()));
    return new ArrayList<>(browsers);
  }

  private static String cellKey(String specPath, String browser) {
    return specPath + " " + browser;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> itemsOf(Map<String, Object> result) {
    Object items = result.get("items");
    return items instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
  }

  private static ItemStatus statusOf(Object value) {
    try {
      return ItemStatus.valueOf(String.valueOf(value));
    } catch (IllegalArgumentException e) {
      return ItemStatus.ERROR;
    }
  }

  private static Integer intOf(Object value) {
    return value instanceof Number number ? number.intValue() : null;
  }

  private static String stringOf(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static ThreadFactory named() {
    AtomicInteger counter = new AtomicInteger();
    return runnable -> {
      Thread thread = new Thread(runnable, "specra-run-" + counter.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    };
  }
}
