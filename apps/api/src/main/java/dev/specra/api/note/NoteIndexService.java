package dev.specra.api.note;

import dev.specra.api.ai.RagService;
import dev.specra.api.note.dto.NoteResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges the note domain to the vector store. Kept out of {@link NoteService} so plain CRUD stays
 * usable with the AI stack switched off.
 */
@Service
public class NoteIndexService {

  private final NoteRepository repository;
  private final NoteService noteService;
  private final NoteMapper mapper;
  private final RagService rag;

  public NoteIndexService(
      NoteRepository repository, NoteService noteService, NoteMapper mapper, RagService rag) {
    this.repository = repository;
    this.noteService = noteService;
    this.mapper = mapper;
    this.rag = rag;
  }

  @Transactional
  public NoteResponse index(UUID id) {
    Note note = noteService.require(id);
    rag.replaceDocument(
        note.getId().toString(),
        note.getContent(),
        Map.of(
            "noteId", note.getId().toString(),
            "title", note.getTitle(),
            "tags", String.join(",", note.getTags())));
    note.setIndexedAt(Instant.now());
    return mapper.toResponse(repository.save(note));
  }

  @Transactional
  public void removeFromIndex(UUID id) {
    rag.deleteBySource(id.toString());
  }
}
