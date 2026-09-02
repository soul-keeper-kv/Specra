package dev.specra.api.feature.run.domain;

/** What asked for the run. Scheduled and CI triggers are later; the column is ready for them. */
public enum RunTrigger {
  MANUAL,
  SCHEDULE,
  CI
}
