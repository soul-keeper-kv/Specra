package dev.specra.api.feature.workspace.web;

import dev.specra.api.core.web.PageResponse;
import dev.specra.api.feature.workspace.dto.WorkspaceRequest;
import dev.specra.api.feature.workspace.dto.WorkspaceResponse;
import dev.specra.api.feature.workspace.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces")
@Tag(name = "Workspaces", description = "The tenant boundary: every project belongs to one.")
public class WorkspaceController {

  private final WorkspaceService service;

  public WorkspaceController(WorkspaceService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "List workspaces, newest first")
  public PageResponse<WorkspaceResponse> list(
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return service.list(pageable);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Fetch one workspace")
  public WorkspaceResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping
  @Operation(
      summary = "Create a workspace; the slug is derived from the name when omitted",
      description = "Whoever creates it owns it — that is the first membership.")
  public ResponseEntity<WorkspaceResponse> create(@Valid @RequestBody WorkspaceRequest request) {
    WorkspaceResponse created = service.create(request);
    return ResponseEntity.created(URI.create("/api/v1/workspaces/" + created.id())).body(created);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Rename a workspace, or move it to a new slug")
  public WorkspaceResponse update(
      @PathVariable UUID id, @Valid @RequestBody WorkspaceRequest request) {
    return service.update(id, request);
  }

  @DeleteMapping("/{id}")
  @Operation(
      summary = "Delete a workspace and everything in it",
      description = "Owners only. Projects, test cases and memberships go with it.")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
