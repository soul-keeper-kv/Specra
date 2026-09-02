package dev.specra.api.feature.run.domain;

/** One matrix cell's outcome. {@code SKIPPED} is the engine's own, not a Specra decision. */
public enum ItemStatus {
  QUEUED,
  RUNNING,
  PASSED,
  FAILED,
  ERROR,
  SKIPPED
}
