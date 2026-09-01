package dev.specra.api.feature.note.web;

import dev.specra.api.core.web.PageResponse;
import dev.specra.api.feature.note.dto.NoteRequest;
import dev.specra.api.feature.note.dto.NoteResponse;
import dev.specra.api.feature.note.service.NoteIndexService;
import dev.specra.api.feature.note.service.NoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notes")
@Tag(name = "Notes", description = "CRUD over PostgreSQL via Spring Data JPA.")
public class NoteController {

  private final NoteService service;
  private final NoteIndexService indexService;

  public NoteController(NoteService service, NoteIndexService indexService) {
    this.service = service;
    this.indexService = indexService;
  }

  @GetMapping
  @Operation(summary = "List notes, newest first, with optional full-text and tag filters")
  public PageResponse<NoteResponse> list(
      @Parameter(description = "Matches title or content, case-insensitive")
          @RequestParam(required = false)
          String q,
      @Parameter(description = "Exact tag match") @RequestParam(required = false) String tag,
      @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return service.search(q, tag, pageable);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Fetch one note")
  public NoteResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping
  @Operation(summary = "Create a note")
  public ResponseEntity<NoteResponse> create(@Valid @RequestBody NoteRequest request) {
    NoteResponse created = service.create(request);
    return ResponseEntity.created(URI.create("/api/notes/" + created.id())).body(created);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Replace a note; clears its index flag because the content changed")
  public NoteResponse update(@PathVariable UUID id, @Valid @RequestBody NoteRequest request) {
    return service.update(id, request);
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete a note; its embeddings are dropped once the delete has committed")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/index")
  @Operation(summary = "Chunk, embed and store this note in pgvector so RAG can retrieve it")
  public NoteResponse index(@PathVariable UUID id) {
    return indexService.index(id);
  }
}
