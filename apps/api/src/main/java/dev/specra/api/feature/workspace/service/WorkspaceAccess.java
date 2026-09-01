package dev.specra.api.feature.workspace.service;

import dev.specra.api.core.error.ForbiddenException;
import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.core.i18n.LocalizedText;
import dev.specra.api.core.security.CurrentUser;
import dev.specra.api.core.security.Permission;
import dev.specra.api.feature.workspace.domain.WorkspaceMemberRepository;
import dev.specra.api.feature.workspace.domain.WorkspaceRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The authorisation decision, in one place: may the caller do this in this workspace?
 *
 * <p>Every feature under a workspace asks this and nothing else — which is what stops the answer
 * from being spelled out slightly differently in five services, and what makes "who can do what"
 * readable from {@link WorkspaceRole} rather than from a search across the code base.
 *
 * <p><b>A stranger gets a 404, not a 403.</b> Telling someone that a workspace exists but is not
 * theirs is telling them something they had no way to know, and it turns a list of guessed ids into
 * a census of the installation's tenants. A 403 is reserved for a member who is short a permission,
 * where the caller already knows the workspace exists because they are in it.
 */
@Service
@Transactional(readOnly = true)
public class WorkspaceAccess {

  private final WorkspaceMemberRepository members;

  public WorkspaceAccess(WorkspaceMemberRepository members) {
    this.members = members;
  }

  /**
   * Asserts that the caller may exercise {@code permission} here, and hands back the role they have
   * — callers that also need to compare ranks (a member edit) get it without a second query.
   */
  public WorkspaceRole require(UUID workspaceId, Permission permission) {
    WorkspaceRole role = requireMember(workspaceId);
    if (!role.can(permission)) {
      throw new ForbiddenException(
          "error.forbidden.permission",
          new LocalizedText(permission.messageKey()),
          new LocalizedText(role.messageKey()));
    }
    return role;
  }

  /** Membership alone. Everything a member may do starts here, so this is the 404 boundary. */
  public WorkspaceRole requireMember(UUID workspaceId) {
    return roleOf(workspaceId)
        .orElseThrow(() -> new ResourceNotFoundException("resource.workspace", workspaceId));
  }

  public Optional<WorkspaceRole> roleOf(UUID workspaceId) {
    return members.findRole(workspaceId, CurrentUser.requireId());
  }

  /**
   * For a query that spans tenants — the assistant's content search — rather than one workspace.
   */
  public List<UUID> visibleWorkspaceIds() {
    return members.findWorkspaceIdsOf(CurrentUser.requireId());
  }
}
