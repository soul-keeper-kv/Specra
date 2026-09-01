package dev.specra.api.feature.testmanagement.web;

import dev.specra.api.feature.testmanagement.dto.TestManagementConnectionRequest;
import dev.specra.api.feature.testmanagement.dto.TestManagementConnectionResponse;
import dev.specra.api.feature.testmanagement.service.TestManagementConnectionService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/test-management-connections")
@Tag(name = "Test management connections", description = "Encrypted provider connections.")
public class TestManagementConnectionController {
  private final TestManagementConnectionService service;

  public TestManagementConnectionController(TestManagementConnectionService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "List connections without returning credentials")
  public List<TestManagementConnectionResponse> list(@PathVariable UUID workspaceId) {
    return service.list(workspaceId);
  }

  @PostMapping
  @Operation(summary = "Store an encrypted provider connection")
  public ResponseEntity<TestManagementConnectionResponse> create(
      @PathVariable UUID workspaceId, @Valid @RequestBody TestManagementConnectionRequest request) {
    TestManagementConnectionResponse created = service.create(workspaceId, request);
    return ResponseEntity.created(
            URI.create(
                "/api/v1/workspaces/"
                    + workspaceId
                    + "/test-management-connections/"
                    + created.id()))
        .body(created);
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete a connection that is not bound to a project")
  public ResponseEntity<Void> delete(@PathVariable UUID workspaceId, @PathVariable UUID id) {
    service.delete(workspaceId, id);
    return ResponseEntity.noContent().build();
  }
}
