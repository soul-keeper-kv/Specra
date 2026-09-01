package dev.specra.api.feature.note.service;

import dev.specra.api.core.content.ContentCapability;
import dev.specra.api.core.content.ContentDocument;
import dev.specra.api.core.content.ContentDraft;
import dev.specra.api.core.content.ContentQuery;
import dev.specra.api.core.content.ContentStore;
import dev.specra.api.core.content.ContentSummary;
import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.core.web.PageResponse;
import dev.specra.api.feature.note.dto.NoteRequest;
import dev.specra.api.feature.note.dto.NoteResponse;
import jakarta.validation.Valid;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * The {@code notes} table, seen through the generic content port.
 *
 * <p>The adapter lives in the note feature, not in {@code core}, so the dependency points inward:
 * {@code core} defines the shape, the feature satisfies it, and nothing in {@code core} knows that
 * notes exist.
 *
 * <p>Writes re-index through {@link NoteIndexService} before returning, which plain CRUD does not
 * do. A user who edits a note in the UI can be left to re-index when it suits them; an assistant
 * that edits one mid-conversation cannot, because the very next question would be answered from the
 * chunks of the text it just replaced.
 *
 * <p>The ordering works because {@link NoteService} publishes its events transactionally: by the
 * time {@code update} returns, the AFTER_COMMIT listener has already dropped the stale chunks, so
 * indexing here adds the new ones on top of nothing rather than racing the delete.
 */
@Component
@Validated
public class NoteContentStore implements ContentStore {

  public static final String KIND = "note";

  private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "updatedAt");

  private final NoteService notes;
  private final NoteIndexService index;

  public NoteContentStore(NoteService notes, NoteIndexService index) {
    this.notes = notes;
    this.index = index;
  }

  @Override
  public String kind() {
    return KIND;
  }

  @Override
  public Set<ContentCapability> capabilities() {
    return EnumSet.allOf(ContentCapability.class);
  }

  @Override
  public PageResponse<ContentSummary> search(ContentQuery query) {
    Pageable pageable = PageRequest.of(query.page(), query.size(), NEWEST_FIRST);
    return notes.search(query.text(), query.tag(), pageable).map(NoteContentStore::toSummary);
  }

  @Override
  public ContentDocument get(String id) {
    return toDocument(notes.get(parseId(id)));
  }

  @Override
  public ContentDocument create(@Valid ContentDraft draft) {
    NoteResponse created = notes.create(toRequest(draft));
    return toDocument(index.index(created.id()));
  }

  @Override
  public ContentDocument update(String id, @Valid ContentDraft draft) {
    NoteResponse updated = notes.update(parseId(id), toRequest(draft));
    return toDocument(index.index(updated.id()));
  }

  @Override
  public void delete(String id) {
    // No call to NoteIndexService here on purpose: NoteService publishes NoteDeleted and the
    // embeddings are dropped after that transaction commits. Dropping them first would strand
    // the chunks whenever the delete then fails, which is the bug the event ordering exists to
    // prevent.
    notes.delete(parseId(id));
  }

  /**
   * An id this store could never have issued names nothing, so it is a 404 rather than a 400.
   * Callers — models included — get one consistent answer for "no such document" instead of having
   * to tell two failures apart, and the reply never advertises that ids happen to be UUIDs.
   */
  private static UUID parseId(String id) {
    try {
      return UUID.fromString(id.strip());
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new ResourceNotFoundException("resource.note", id);
    }
  }

  private static NoteRequest toRequest(ContentDraft draft) {
    return new NoteRequest(draft.title(), draft.body(), draft.tags());
  }

  private static ContentDocument toDocument(NoteResponse note) {
    return new ContentDocument(
        KIND,
        note.id().toString(),
        note.title(),
        note.content(),
        note.tags(),
        note.createdAt(),
        note.updatedAt());
  }

  private static ContentSummary toSummary(NoteResponse note) {
    return new ContentSummary(
        KIND,
        note.id().toString(),
        note.title(),
        ContentSummary.excerpt(note.content()),
        note.tags(),
        note.updatedAt());
  }
}
