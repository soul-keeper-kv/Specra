package dev.specra.api.feature.run.domain;

/**
 * What the engine kept from a failure (06-execution.md).
 *
 * <p>Playwright already produces exactly what failure analysis needs, so the job is to keep it
 * rather than reinvent it: the trace is the most useful debugging artefact, the video is what a
 * non-technical QA will actually watch, and the DOM snapshot is what the locator planner reads to
 * propose a fix.
 */
public enum ArtifactKind {
  SCREENSHOT,
  VIDEO,
  TRACE,
  LOG,
  DOM
}
