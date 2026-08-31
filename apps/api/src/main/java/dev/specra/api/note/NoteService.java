package dev.specra.api.note;

import dev.specra.api.common.NotFoundException;
import dev.specra.api.common.PageResponse;
import dev.specra.api.note.dto.NoteRequest;
import dev.specra.api.note.dto.NoteResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class NoteService {

  private final NoteRepository repository;
  private final NoteMapper mapper;

  public NoteService(NoteRepository repository, NoteMapper mapper) {
    this.repository = repository;
    this.mapper = mapper;
  }

  public PageResponse<NoteResponse> search(String q, String tag, Pageable pageable) {
    String query = StringUtils.hasText(q) ? q.trim() : null;
    String tagFilter = StringUtils.hasText(tag) ? tag.trim().toLowerCase() : null;
    return PageResponse.from(repository.search(query, tagFilter, pageable), mapper::toResponse);
  }

  public NoteResponse get(UUID id) {
    return mapper.toResponse(require(id));
  }

  Note require(UUID id) {
    return repository.findById(id).orElseThrow(() -> new NotFoundException("Note", id));
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
    return mapper.toResponse(repository.save(note));
  }

  @Transactional
  public void delete(UUID id) {
    if (!repository.existsById(id)) {
      throw new NotFoundException("Note", id);
    }
    repository.deleteById(id);
  }
}
