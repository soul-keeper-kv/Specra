package dev.specra.api.feature.codegen.domain;

/**
 * A proposal's life (01-domain-model.md).
 *
 * <pre>
 * PROPOSED ──┬──► APPLIED      a person accepted it; a commit exists
 *            ├──► REJECTED
 *            └──► SUPERSEDED   a newer generation replaced it
 * </pre>
 *
 * <p>There is no transition from {@code PROPOSED} to a file on disk that a person did not make.
 * That is the human-in-the-loop principle, written as a state machine rather than a paragraph.
 */
public enum CodeGenerationStatus {
  PROPOSED,
  APPLIED,
  REJECTED,
  SUPERSEDED
}
