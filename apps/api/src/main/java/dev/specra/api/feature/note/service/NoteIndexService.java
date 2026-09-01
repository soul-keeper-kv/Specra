package dev.specra.api.feature.note.service;

import dev.specra.api.feature.ai.service.DocumentIndexService;
import dev.specra.api.feature.note.domain.Note;
import dev.specra.api.feature.note.dto.NoteResponse;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The one class that knows both a note and the vector store.
 *
 * <p>It is kept out of {@link NoteService} on purpose, so plain CRUD stays usable with the AI stack
 * switched off, and it reacts to {@link NoteEvents} rather than being called by it — the dependency
 * only ever points this way, note to AI, never back.
 *
 * <p>It reaches the table through {@link NoteService} rather than through the repository, so there
 * is still exactly one place that decides what a missing note means and what a save looks like.
 */
@Service
public class NoteIndexService {

  private static final Logger log = LoggerFactory.getLogger(NoteIndexService.class);

  private final NoteService notes;
  private final DocumentIndexService documentIndex;

  public NoteIndexService(NoteService notes, DocumentIndexService documentIndex) {
    this.notes = notes;
    this.documentIndex = documentIndex;
  }

  /**
   * Deliberately not {@code @Transactional}: embedding the text is a round trip to a model provider
   * that can take seconds, and wrapping the whole method would hold a database connection open for
   * all of it. Reading the note and marking it indexed are two short transactions of their own.
   */
  public NoteResponse index(UUID id) {
    Note note = notes.require(id);

    documentIndex.replaceDocument(
        note.getId().toString(),
        note.getContent(),
        Map.of(
            "noteId", note.getId().toString(),
            "title", note.getTitle(),
            "tags", String.join(",", note.getTags())));

    return notes.markIndexed(id);
  }

  /**
   * Drop a note's embeddings on demand. Deleting through {@link NoteService} already does this on
   * its own — see {@link #onNoteDeleted} — so this is for a caller that has to drop them without
   * deleting the row. It is idempotent: removing chunks that are already gone is a no-op.
   */
  public void removeFromIndex(UUID noteId) {
    documentIndex.deleteBySource(noteId.toString());
  }

  /**
   * After commit, not before: if the delete rolls back, the note is still there and its embeddings
   * must still be retrievable.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onNoteDeleted(NoteEvents.NoteDeleted event) {
    removeQuietly(event.noteId());
  }

  /**
   * An edited note keeps its id but not its text, so the chunks embedded from the old text would
   * still answer questions with content nobody can see any more. {@code indexedAt} is already null
   * by now, so the note simply shows as un-indexed until someone indexes it again.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onNoteContentChanged(NoteEvents.NoteContentChanged event) {
    removeQuietly(event.noteId());
  }

  /**
   * The write the caller asked for has already committed, so failing here must not turn a
   * successful request into a 500. It is logged loudly instead: the row is correct, the index is
   * behind, and re-indexing fixes it.
   */
  private void removeQuietly(UUID noteId) {
    try {
      documentIndex.deleteBySource(noteId.toString());
    } catch (RuntimeException e) {
      log.error("Could not drop embeddings for note {} — the index is now stale", noteId, e);
    }
  }
}
