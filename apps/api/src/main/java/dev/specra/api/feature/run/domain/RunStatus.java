package dev.specra.api.feature.run.domain;

/**
 * Where a run is, and how it ended (01-domain-model.md).
 *
 * <p>{@code FAILED} and {@code ERROR} are deliberately different. FAILED means the tests ran and an
 * assertion or a locator did not hold — that says something about the application under test and is
 * the only outcome worth an AI failure analysis. ERROR means the run could not complete: an
 * install, a build, a timeout, the runner being down. That is usually ours to fix, and asking a
 * model to explain it would be asking it to diagnose our infrastructure from a test's evidence.
 */
public enum RunStatus {
  QUEUED,
  RUNNING,
  PASSED,
  FAILED,
  ERROR,
  CANCELLED;

  /** True once the run has stopped, whichever way it went. */
  public boolean isTerminal() {
    return this != QUEUED && this != RUNNING;
  }
}
