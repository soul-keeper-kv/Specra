package dev.specra.api.feature.note.service;

import java.util.UUID;

/**
 * What {@link NoteService} announces after it has changed a note.
 *
 * <p>They exist so plain CRUD does not have to know that a vector store exists. {@link
 * NoteIndexService} listens for them after the transaction commits and keeps the embeddings in
 * step; with the AI stack removed, nothing listens and CRUD still works unchanged.
 */
public final class NoteEvents {

  private NoteEvents() {}

  /** The note is gone, so anything embedded from it is now unreachable content. */
  public record NoteDeleted(UUID noteId) {}

  /** The text changed, so any chunk already embedded is stale and must not stay retrievable. */
  public record NoteContentChanged(UUID noteId) {}
}
