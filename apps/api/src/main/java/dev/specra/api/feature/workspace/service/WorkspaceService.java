package dev.specra.api.feature.workspace.service;

import dev.specra.api.core.error.ConflictException;
import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.core.security.CurrentUser;
import dev.specra.api.core.security.Permission;
import dev.specra.api.core.text.Slugs;
import dev.specra.api.core.web.PageResponse;
import dev.specra.api.feature.auth.domain.User;
import dev.specra.api.feature.auth.service.AuthService;
import dev.specra.api.feature.workspace.domain.Workspace;
import dev.specra.api.feature.workspace.domain.WorkspaceMember;
import dev.specra.api.feature.workspace.domain.WorkspaceMemberRepository;
import dev.specra.api.feature.workspace.domain.WorkspaceRepository;
import dev.specra.api.feature.workspace.domain.WorkspaceRole;
import dev.specra.api.feature.workspace.dto.WorkspaceRequest;
import dev.specra.api.feature.workspace.dto.WorkspaceResponse;
import dev.specra.api.feature.workspace.mapper.WorkspaceMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The tenant: created, listed, and asked whether the caller may act on it by every feature
 * underneath it.
 *
 * <p>Nothing here is reachable without a membership. A listing returns the caller's workspaces
 * rather than the installation's, and every other entry point goes through {@link WorkspaceAccess}
 * — which is why {@link #requireAccess} replaced the older existence check: on a multi-tenant API,
 * "does this row exist" is the wrong question and answering it truthfully is a leak.
 */
@Service
@Transactional(readOnly = true)
public class WorkspaceService {

  /** What a name made entirely of characters no slug can carry falls back to. */
  private static final String FALLBACK_SLUG = "workspace";

  private static final int SLUG_MAX = 64;

  private final WorkspaceRepository repository;
  private final WorkspaceMemberRepository members;
  private final WorkspaceMapper mapper;
  private final WorkspaceAccess access;
  private final AuthService users;

  public WorkspaceService(
      WorkspaceRepository repository,
      WorkspaceMemberRepository members,
      WorkspaceMapper mapper,
      WorkspaceAccess access,
      AuthService users) {
    this.repository = repository;
    this.members = members;
    this.mapper = mapper;
    this.access = access;
    this.users = users;
  }

  /** The caller's workspaces, each carrying the role they hold in it. */
  public PageResponse<WorkspaceResponse> list(Pageable pageable) {
    return PageResponse.from(
        members.findByUserId(CurrentUser.requireId(), pageable),
        member -> mapper.toResponse(member.getWorkspace(), member.getRole()));
  }

  public WorkspaceResponse get(UUID id) {
    WorkspaceRole role = access.requireMember(id);
    return mapper.toResponse(require(id), role);
  }

  /**
   * The gate every feature under a workspace passes through.
   *
   * <p>It delegates to {@link WorkspaceAccess} rather than being called directly so the arrow keeps
   * pointing the way {@code ProjectService} already documents — a child feature reaches its parent
   * through this service, never through that feature's repository or its internals.
   */
  public WorkspaceRole requireAccess(UUID workspaceId, Permission permission) {
    return access.require(workspaceId, permission);
  }

  /** The workspaces the caller can see at all, for the cross-tenant search the assistant makes. */
  public List<UUID> visibleWorkspaceIds() {
    return access.visibleWorkspaceIds();
  }

  /**
   * Whoever creates a workspace owns it; there is no other way for the first membership to exist.
   */
  @Transactional
  public WorkspaceResponse create(WorkspaceRequest request) {
    User owner = users.require(CurrentUser.requireId());
    Workspace workspace = new Workspace();
    workspace.setName(request.name().trim());
    workspace.setSlug(resolveSlug(request));
    Workspace saved = repository.save(workspace);

    members.save(WorkspaceMember.of(saved, owner, WorkspaceRole.OWNER));
    return mapper.toResponse(saved, WorkspaceRole.OWNER);
  }

  @Transactional
  public WorkspaceResponse update(UUID id, WorkspaceRequest request) {
    WorkspaceRole role = requireAccess(id, Permission.WORKSPACE_UPDATE);
    Workspace workspace = require(id);
    workspace.setName(request.name().trim());
    if (StringUtils.hasText(request.slug()) && !request.slug().trim().equals(workspace.getSlug())) {
      String requested = request.slug().trim();
      if (repository.existsBySlug(requested)) {
        throw new ConflictException("error.workspace.duplicate-slug", requested);
      }
      workspace.setSlug(requested);
    }
    return mapper.toResponse(repository.save(workspace), role);
  }

  /**
   * Takes the projects, test cases and memberships with it — the cascades are declared in V2. Only
   * an owner has {@code WORKSPACE_DELETE}, which is the difference between an administrator whose
   * mistakes can be repaired and one whose cannot.
   */
  @Transactional
  public void delete(UUID id) {
    requireAccess(id, Permission.WORKSPACE_DELETE);
    repository.delete(require(id));
  }

  /**
   * The workspace a new account lands in, created by {@link WorkspaceProvisioning} once
   * registration has committed.
   *
   * <p>It exists so that signing up leaves somebody able to do something. A product whose first
   * screen after registration is "create a workspace" has made its own data model the user's first
   * task.
   */
  @Transactional
  public WorkspaceResponse createFor(UUID userId, String name) {
    User owner = users.require(userId);
    Workspace workspace = new Workspace();
    workspace.setName(name);
    workspace.setSlug(resolveSlug(new WorkspaceRequest(name, null)));
    Workspace saved = repository.save(workspace);

    members.save(WorkspaceMember.of(saved, owner, WorkspaceRole.OWNER));
    return mapper.toResponse(saved, WorkspaceRole.OWNER);
  }

  private Workspace require(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("resource.workspace", id));
  }

  /**
   * A slug the caller chose is theirs: taken, it is a conflict they can act on. A slug we derived
   * is an implementation detail, so a collision gets a numeric suffix instead of an error the user
   * has no way to understand.
   */
  private String resolveSlug(WorkspaceRequest request) {
    if (StringUtils.hasText(request.slug())) {
      String requested = request.slug().trim();
      if (repository.existsBySlug(requested)) {
        throw new ConflictException("error.workspace.duplicate-slug", requested);
      }
      return requested;
    }

    String base = Slugs.of(request.name(), SLUG_MAX);
    if (base.isEmpty()) {
      base = FALLBACK_SLUG;
    }
    String candidate = base;
    for (int suffix = 2; repository.existsBySlug(candidate); suffix++) {
      String tail = "-" + suffix;
      candidate = base.substring(0, Math.min(base.length(), SLUG_MAX - tail.length())) + tail;
    }
    return candidate;
  }
}
