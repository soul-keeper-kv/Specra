package dev.specra.api.feature.workspace.web;

import dev.specra.api.feature.workspace.dto.AiAccountRequest;
import dev.specra.api.feature.workspace.dto.AiAccountResponse;
import dev.specra.api.feature.workspace.service.AiAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A singleton sub-resource of the workspace: at most one account, addressed by its parent. */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/ai-account")
@Tag(
    name = "AI account",
    description = "The workspace's own provider, key and budget. The key is write-only.")
public class AiAccountController {

  private final AiAccountService service;

  public AiAccountController(AiAccountService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Fetch the workspace's AI account; 404 when it uses the platform default")
  public AiAccountResponse get(@PathVariable UUID workspaceId) {
    return service.get(workspaceId);
  }

  @PutMapping
  @Operation(summary = "Create or replace the account; a null apiKey keeps the stored one")
  public AiAccountResponse put(
      @PathVariable UUID workspaceId, @Valid @RequestBody AiAccountRequest request) {
    return service.put(workspaceId, request);
  }

  @DeleteMapping
  @Operation(summary = "Remove the account so the workspace falls back to the platform provider")
  public ResponseEntity<Void> delete(@PathVariable UUID workspaceId) {
    service.delete(workspaceId);
    return ResponseEntity.noContent().build();
  }
}
