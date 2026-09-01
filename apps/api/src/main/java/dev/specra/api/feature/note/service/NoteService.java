package dev.specra.api.feature.note.service;

import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.core.web.PageResponse;
import dev.specra.api.feature.note.domain.Note;
import dev.specra.api.feature.note.domain.NoteRepository;
import dev.specra.api.feature.note.dto.NoteRequest;
import dev.specra.api.feature.note.dto.NoteResponse;
import dev.specra.api.feature.note.mapper.NoteMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Everything a note does on its own: storage, search, turning a request into an entity.
 *
 * <p>It knows nothing about embeddings. When a change makes the vector store stale it says so with
 * a {@link NoteEvents} record and lets whoever cares react — see {@link NoteIndexService}. Removing
 * the AI stack would leave this class untouched.
 */
@Service
@Transactional(readOnly = true)
public class NoteService {

  private final NoteRepository repository;
  private final NoteMapper mapper;
  private final ApplicationEventPublisher events;

  public NoteService(
      NoteRepository repository, NoteMapper mapper, ApplicationEventPublisher events) {
    this.repository = repository;
    this.mapper = mapper;
    this.events = events;
  }

  public PageResponse<NoteResponse> search(String q, String tag, Pageable pageable) {
    String query = StringUtils.hasText(q) ? q.trim() : null;
    String tagFilter = StringUtils.hasText(tag) ? tag.trim().toLowerCase() : null;
    return PageResponse.from(repository.search(query, tagFilter, pageable), mapper::toResponse);
  }

  public NoteResponse get(UUID id) {
    return mapper.toResponse(require(id));
  }

  /**
   * The one throw site for a missing note, so every caller reports it identically. Package-private
   * and returning the entity: it is for the other service in this feature, not for the web layer,
   * which only ever sees a {@link NoteResponse}.
   */
  Note require(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("resource.note", id));
  }

  @Transactional
  public NoteResponse create(NoteRequest request) {
    Note note = mapper.toEntity(request);
    note.replaceTags(request.tags());
    return mapper.toResponse(repository.save(note));
  }

  @Transactional
  public NoteResponse update(UUID id, NoteRequest request) {
    Note note = require(id);
    mapper.update(note, request);
    note.replaceTags(request.tags());
    // Content changed, so anything already embedded is stale.
    note.setIndexedAt(null);
    NoteResponse response = mapper.toResponse(repository.save(note));
    events.publishEvent(new NoteEvents.NoteContentChanged(id));
    return response;
  }

  @Transactional
  public void delete(UUID id) {
    repository.delete(require(id));
    events.publishEvent(new NoteEvents.NoteDeleted(id));
  }

  /**
   * Records that the note's current text is in the vector store. Its own transaction, called by
   * {@link NoteIndexService} once the slow part — embedding — is over.
   */
  @Transactional
  NoteResponse markIndexed(UUID id) {
    Note note = require(id);
    note.setIndexedAt(Instant.now());
    return mapper.toResponse(repository.save(note));
  }
}
