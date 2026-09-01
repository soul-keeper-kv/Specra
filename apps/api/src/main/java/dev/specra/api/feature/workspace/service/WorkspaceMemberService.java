package dev.specra.api.feature.workspace.service;

import dev.specra.api.core.error.ConflictException;
import dev.specra.api.core.error.ForbiddenException;
import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.core.i18n.LocalizedText;
import dev.specra.api.core.security.CurrentUser;
import dev.specra.api.core.security.Permission;
import dev.specra.api.core.web.PageResponse;
import dev.specra.api.feature.auth.domain.User;
import dev.specra.api.feature.auth.service.AuthService;
import dev.specra.api.feature.workspace.domain.Workspace;
import dev.specra.api.feature.workspace.domain.WorkspaceMember;
import dev.specra.api.feature.workspace.domain.WorkspaceMemberRepository;
import dev.specra.api.feature.workspace.domain.WorkspaceRepository;
import dev.specra.api.feature.workspace.domain.WorkspaceRole;
import dev.specra.api.feature.workspace.dto.MemberAddRequest;
import dev.specra.api.feature.workspace.dto.MemberResponse;
import dev.specra.api.feature.workspace.dto.MemberRoleRequest;
import dev.specra.api.feature.workspace.mapper.WorkspaceMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Role management: who is in a workspace, and as what.
 *
 * <p>The permission checks are the easy half and {@link WorkspaceAccess} does them. The rules worth
 * reading are the four this class adds on top, because each of them is a way a workspace could
 * otherwise be left broken or captured:
 *
 * <ul>
 *   <li><b>Nobody may change their own role.</b> Otherwise an administrator promotes themselves to
 *       owner, and the distinction between the two roles is decoration.
 *   <li><b>Nobody may act on a role senior to their own, or grant one.</b> An administrator
 *       removing the owner is the same takeover by a different route.
 *   <li><b>The last owner cannot be demoted or removed.</b> A workspace with no owner is a
 *       workspace nobody can ever administer again, and no endpoint can undo it.
 *   <li><b>Leaving is not the same as being removed</b>, so it needs no permission — but the last
 *       owner cannot leave either, for the same reason.
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class WorkspaceMemberService {

  private static final Logger log = LoggerFactory.getLogger(WorkspaceMemberService.class);

  private final WorkspaceMemberRepository members;
  private final WorkspaceRepository workspaces;
  private final WorkspaceMapper mapper;
  private final WorkspaceAccess access;
  private final AuthService users;

  public WorkspaceMemberService(
      WorkspaceMemberRepository members,
      WorkspaceRepository workspaces,
      WorkspaceMapper mapper,
      WorkspaceAccess access,
      AuthService users) {
    this.members = members;
    this.workspaces = workspaces;
    this.mapper = mapper;
    this.access = access;
    this.users = users;
  }

  public PageResponse<MemberResponse> list(UUID workspaceId, Pageable pageable) {
    access.require(workspaceId, Permission.MEMBER_VIEW);
    return PageResponse.from(members.findByWorkspaceId(workspaceId, pageable), mapper::toResponse);
  }

  /**
   * Adds an existing account. A missing one is a 404 on the address rather than a silent invite:
   * this installation cannot send mail, and an invitation nobody receives is worse than an error.
   */
  @Transactional
  public MemberResponse add(UUID workspaceId, MemberAddRequest request) {
    WorkspaceRole actor = access.require(workspaceId, Permission.MEMBER_ADD);
    requireCanAssign(actor, request.role());

    User user = users.requireByEmail(request.email());
    if (members.existsByWorkspaceIdAndUserId(workspaceId, user.getId())) {
      throw new ConflictException("error.member.already-a-member", user.getEmail());
    }

    Workspace workspace =
        workspaces
            .findById(workspaceId)
            .orElseThrow(() -> new ResourceNotFoundException("resource.workspace", workspaceId));

    WorkspaceMember member = members.save(WorkspaceMember.of(workspace, user, request.role()));
    log.info("Added user {} to workspace {} as {}", user.getId(), workspaceId, request.role());
    return mapper.toResponse(member);
  }

  @Transactional
  public MemberResponse changeRole(UUID workspaceId, UUID userId, MemberRoleRequest request) {
    WorkspaceRole actor = access.require(workspaceId, Permission.MEMBER_UPDATE_ROLE);
    WorkspaceMember member = require(workspaceId, userId);

    requireNotSelf(userId, "error.member.cannot-change-own-role");
    requireCanActOn(actor, member.getRole());
    requireCanAssign(actor, request.role());
    if (member.getRole() == WorkspaceRole.OWNER && request.role() != WorkspaceRole.OWNER) {
      requireNotTheLastOwner(workspaceId);
    }

    member.setRole(request.role());
    log.info("Changed user {} in workspace {} to {}", userId, workspaceId, request.role());
    return mapper.toResponse(members.save(member));
  }

  @Transactional
  public void remove(UUID workspaceId, UUID userId) {
    WorkspaceRole actor = access.require(workspaceId, Permission.MEMBER_REMOVE);
    WorkspaceMember member = require(workspaceId, userId);

    requireNotSelf(userId, "error.member.cannot-remove-self");
    requireCanActOn(actor, member.getRole());
    if (member.getRole() == WorkspaceRole.OWNER) {
      requireNotTheLastOwner(workspaceId);
    }

    members.delete(member);
    log.info("Removed user {} from workspace {}", userId, workspaceId);
  }

  /**
   * Leaving under your own steam, which needs no permission — every member may. The last owner
   * still cannot, because the workspace would be left with nobody able to administer it.
   */
  @Transactional
  public void leave(UUID workspaceId) {
    UUID userId = CurrentUser.requireId();
    WorkspaceMember member = require(workspaceId, userId);
    if (member.getRole() == WorkspaceRole.OWNER) {
      requireNotTheLastOwner(workspaceId);
    }
    members.delete(member);
    log.info("User {} left workspace {}", userId, workspaceId);
  }

  private WorkspaceMember require(UUID workspaceId, UUID userId) {
    return members
        .findByWorkspaceIdAndUserId(workspaceId, userId)
        .orElseThrow(() -> new ResourceNotFoundException("resource.member", userId));
  }

  private static void requireNotSelf(UUID userId, String messageKey) {
    if (userId.equals(CurrentUser.requireId())) {
      throw new ForbiddenException(messageKey);
    }
  }

  /**
   * You may not touch somebody senior to you. Somebody at your own rank you may: two owners have to
   * be able to hand the workspace over and tidy up after each other, and the same is true of
   * administrators. What stops that from becoming a coup is the pair of rules either side of it —
   * nobody edits their own row, and the last owner cannot be demoted.
   */
  private static void requireCanActOn(WorkspaceRole actor, WorkspaceRole target) {
    if (!actor.outranksOrEquals(target)) {
      throw new ForbiddenException(
          "error.member.outranked", new LocalizedText(target.messageKey()));
    }
  }

  /** Nor hand out a role you do not hold: granting OWNER is a thing only an owner can do. */
  private static void requireCanAssign(WorkspaceRole actor, WorkspaceRole granted) {
    if (!actor.outranksOrEquals(granted)) {
      throw new ForbiddenException(
          "error.member.cannot-grant", new LocalizedText(granted.messageKey()));
    }
  }

  private void requireNotTheLastOwner(UUID workspaceId) {
    if (members.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER) <= 1) {
      throw new ConflictException("error.member.last-owner");
    }
  }
}
