package dev.specra.api.feature.workspace.web;

import dev.specra.api.core.web.PageResponse;
import dev.specra.api.feature.workspace.dto.MemberAddRequest;
import dev.specra.api.feature.workspace.dto.MemberResponse;
import dev.specra.api.feature.workspace.dto.MemberRoleRequest;
import dev.specra.api.feature.workspace.service.WorkspaceMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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
 * The people in a workspace, addressed by user id rather than by membership id — that is what the
 * caller has after listing, and what the member screen already shows.
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/members")
@Tag(
    name = "Members",
    description =
        "Who is in a workspace and as what. A role is per workspace: the same person can own one "
            + "and only read another.")
public class WorkspaceMemberController {

  private final WorkspaceMemberService service;

  public WorkspaceMemberController(WorkspaceMemberService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "List the members of a workspace")
  public PageResponse<MemberResponse> list(
      @PathVariable UUID workspaceId,
      @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.ASC)
          Pageable pageable) {
    return service.list(workspaceId, pageable);
  }

  @PostMapping
  @Operation(
      summary = "Add an existing account to this workspace",
      description =
          "The address must already have an account: this installation cannot send mail, so an "
              + "unknown address is a 404 rather than an invitation nobody receives.")
  public ResponseEntity<MemberResponse> add(
      @PathVariable UUID workspaceId, @Valid @RequestBody MemberAddRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.add(workspaceId, request));
  }

  @PutMapping("/{userId}/role")
  @Operation(
      summary = "Change what someone may do here",
      description =
          "You cannot change your own role, act on somebody at or above your own rank, grant a "
              + "role above your own, or demote the last owner.")
  public MemberResponse changeRole(
      @PathVariable UUID workspaceId,
      @PathVariable UUID userId,
      @Valid @RequestBody MemberRoleRequest request) {
    return service.changeRole(workspaceId, userId, request);
  }

  @DeleteMapping("/{userId}")
  @Operation(summary = "Remove someone from this workspace")
  public ResponseEntity<Void> remove(@PathVariable UUID workspaceId, @PathVariable UUID userId) {
    service.remove(workspaceId, userId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/me")
  @Operation(
      summary = "Leave this workspace",
      description = "Needs no permission — every member may leave, except the last owner.")
  public ResponseEntity<Void> leave(@PathVariable UUID workspaceId) {
    service.leave(workspaceId);
    return ResponseEntity.noContent().build();
  }
}
