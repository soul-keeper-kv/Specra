package dev.specra.api.feature.testcase.domain;

/** How much a failure of this case matters — set by the author, read by run planning later. */
public enum TestCasePriority {
  LOW,
  MEDIUM,
  HIGH,
  CRITICAL
}
