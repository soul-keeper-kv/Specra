package dev.specra.api.feature.environment.web;

import dev.specra.api.feature.environment.dto.EnvironmentRequest;
import dev.specra.api.feature.environment.dto.EnvironmentResponse;
import dev.specra.api.feature.environment.service.EnvironmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where a project's runs point, and what they carry.
 *
 * <p>Deliberately not paged: a project has a handful of environments, and a `PageResponse` around
 * three rows is ceremony that every client then has to unwrap.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(
    name = "Environments",
    description = "Base URL and variables per deployment. Secret values are write-only.")
public class EnvironmentController {

  private final EnvironmentService service;

  public EnvironmentController(EnvironmentService service) {
    this.service = service;
  }

  @GetMapping("/projects/{projectId}/environments")
  @Operation(summary = "The project's environments; secret values are never included")
  public List<EnvironmentResponse> list(@PathVariable UUID projectId) {
    return service.list(projectId);
  }

  @PostMapping("/projects/{projectId}/environments")
  @Operation(summary = "Create an environment; the first one of a project becomes its default")
  public ResponseEntity<EnvironmentResponse> create(
      @PathVariable UUID projectId, @Valid @RequestBody EnvironmentRequest request) {
    EnvironmentResponse created = service.create(projectId, request);
    return ResponseEntity.created(URI.create("/api/v1/environments/" + created.id())).body(created);
  }

  @GetMapping("/environments/{id}")
  @Operation(summary = "One environment")
  public EnvironmentResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PutMapping("/environments/{id}")
  @Operation(
      summary =
          "Replace an environment. A secret variable sent without a value keeps the stored one,"
              + " so the form can be saved without retyping every credential.")
  public EnvironmentResponse update(
      @PathVariable UUID id, @Valid @RequestBody EnvironmentRequest request) {
    return service.update(id, request);
  }

  @DeleteMapping("/environments/{id}")
  @Operation(summary = "Delete an environment and its variables")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
