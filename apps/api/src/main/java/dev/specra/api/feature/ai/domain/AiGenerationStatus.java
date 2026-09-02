package dev.specra.api.feature.ai.domain;

/**
 * The state machine from 01-domain-model.md. {@code PROPOSED} never becomes a file on disk without
 * a user action; that transition is the human-in-the-loop principle written down.
 */
public enum AiGenerationStatus {
  PENDING,
  PROPOSED,
  APPLIED,
  REJECTED,
  SUPERSEDED,
  FAILED
}
