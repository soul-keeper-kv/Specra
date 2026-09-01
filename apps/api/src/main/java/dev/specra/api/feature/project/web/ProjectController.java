package dev.specra.api.feature.project.web;

import dev.specra.api.core.web.PageResponse;
import dev.specra.api.feature.project.dto.ProjectPatchRequest;
import dev.specra.api.feature.project.dto.ProjectRequest;
import dev.specra.api.feature.project.dto.ProjectResponse;
import dev.specra.api.feature.project.service.ProjectService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Two path shapes on purpose: a project is created and listed inside its workspace, and addressed
 * by its own id everywhere after that — which is the only id a link ever has to carry.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Projects", description = "One project is one Git repository and one default branch.")
public class ProjectController {

  private final ProjectService service;

  public ProjectController(ProjectService service) {
    this.service = service;
  }

  @GetMapping("/workspaces/{workspaceId}/projects")
  @Operation(summary = "List the projects in a workspace, most recently updated first")
  public PageResponse<ProjectResponse> list(
      @PathVariable UUID workspaceId,
      @Parameter(description = "Matches name or key, case-insensitive")
          @RequestParam(required = false)
          String q,
      @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return service.search(workspaceId, q, pageable);
  }

  @PostMapping("/workspaces/{workspaceId}/projects")
  @Operation(summary = "Create a project; the key is derived from the name when omitted")
  public ResponseEntity<ProjectResponse> create(
      @PathVariable UUID workspaceId, @Valid @RequestBody ProjectRequest request) {
    ProjectResponse created = service.create(workspaceId, request);
    return ResponseEntity.created(URI.create("/api/v1/projects/" + created.id())).body(created);
  }

  @GetMapping("/projects/{id}")
  @Operation(summary = "Fetch one project")
  public ProjectResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PatchMapping("/projects/{id}")
  @Operation(summary = "Update the name or description; the key and the engine are fixed")
  public ProjectResponse update(
      @PathVariable UUID id, @Valid @RequestBody ProjectPatchRequest request) {
    return service.update(id, request);
  }

  @DeleteMapping("/projects/{id}")
  @Operation(summary = "Delete a project and everything recorded under it")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
