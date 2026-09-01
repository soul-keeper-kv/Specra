package dev.specra.api.feature.git.web;

import dev.specra.api.feature.git.dto.CredentialRequest;
import dev.specra.api.feature.git.dto.CredentialResponse;
import dev.specra.api.feature.git.service.GitCredentialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tokens live on the workspace, not the project: one PAT usually unlocks several repositories.
 * There is no update endpoint — a token is replaced by creating a new credential and pointing the
 * repository at it, which leaves the old one revocable independently.
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/git-credentials")
@Tag(name = "Git credentials", description = "Write-only tokens for reaching remotes.")
public class GitCredentialController {

  private final GitCredentialService service;

  public GitCredentialController(GitCredentialService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "List the workspace's credentials; never their tokens")
  public List<CredentialResponse> list(@PathVariable UUID workspaceId) {
    return service.list(workspaceId);
  }

  @PostMapping
  @Operation(summary = "Store a credential; the token is encrypted and never returned")
  public ResponseEntity<CredentialResponse> create(
      @PathVariable UUID workspaceId, @Valid @RequestBody CredentialRequest request) {
    CredentialResponse created = service.create(workspaceId, request);
    return ResponseEntity.created(
            java.net.URI.create(
                "/api/v1/workspaces/" + workspaceId + "/git-credentials/" + created.id()))
        .body(created);
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete a credential; repositories using it fall back to no auth")
  public ResponseEntity<Void> delete(@PathVariable UUID workspaceId, @PathVariable UUID id) {
    service.delete(workspaceId, id);
    return ResponseEntity.noContent().build();
  }
}
