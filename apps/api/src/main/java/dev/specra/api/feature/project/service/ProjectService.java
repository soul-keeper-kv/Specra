package dev.specra.api.feature.project.service;

import dev.specra.api.core.error.ConflictException;
import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.core.security.Permission;
import dev.specra.api.core.text.Slugs;
import dev.specra.api.core.web.PageResponse;
import dev.specra.api.feature.project.domain.AutomationEngine;
import dev.specra.api.feature.project.domain.Project;
import dev.specra.api.feature.project.domain.ProjectRepository;
import dev.specra.api.feature.project.dto.ProjectPatchRequest;
import dev.specra.api.feature.project.dto.ProjectRequest;
import dev.specra.api.feature.project.dto.ProjectResponse;
import dev.specra.api.feature.project.mapper.ProjectMapper;
import dev.specra.api.feature.workspace.service.WorkspaceService;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Projects, scoped by their workspace.
 *
 * <p>It reaches the workspace through {@link WorkspaceService}, never through that feature's
 * repository: a project may depend on a workspace, and keeping the arrow pointing at the service
 * keeps the entity on its own side of the boundary.
 */
@Service
@Transactional(readOnly = true)
public class ProjectService {

  private static final int KEY_MAX = 16;

  /** What a name with no letters or digits in it falls back to. */
  private static final String FALLBACK_KEY = "PRJ";

  private final ProjectRepository repository;
  private final ProjectMapper mapper;
  private final WorkspaceService workspaces;

  public ProjectService(
      ProjectRepository repository, ProjectMapper mapper, WorkspaceService workspaces) {
    this.repository = repository;
    this.mapper = mapper;
    this.workspaces = workspaces;
  }

  public PageResponse<ProjectResponse> search(UUID workspaceId, String q, Pageable pageable) {
    workspaces.requireAccess(workspaceId, Permission.CONTENT_VIEW);
    String query = StringUtils.hasText(q) ? q.trim() : null;
    return PageResponse.from(repository.search(workspaceId, query, pageable), mapper::toResponse);
  }

  public ProjectResponse get(UUID id) {
    return mapper.toResponse(requireVisible(id, Permission.CONTENT_VIEW));
  }

  /**
   * The one throw site for a project the caller may not have. Public because every feature below a
   * project — test cases first — has to check its parent, and a second lookup written by hand is a
   * second 404 sentence to keep translated.
   *
   * <p>Existence and access are answered together on purpose: a project in somebody else{'}s
   * workspace has to be indistinguishable from one that was never created, or a caller can map the
   * installation by guessing ids.
   */
  public void requireAccess(UUID id, Permission permission) {
    requireVisible(id, permission);
  }

  /** The workspace a project belongs to, for the {@code workspace_id} its children also carry. */
  public UUID workspaceOf(UUID id) {
    return require(id).getWorkspaceId();
  }

  /**
   * Mints the next {@code TC-n} reference for this project, under a row lock so the number is never
   * handed out twice. Joins the caller's transaction: if creating the test case rolls back, the
   * bump rolls back with it — a gap in the sequence is acceptable, a duplicate is not.
   */
  @Transactional
  public String nextTestCaseReference(UUID projectId) {
    Project project =
        repository
            .lockById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("resource.project", projectId));
    project.setTestCaseSequence(project.getTestCaseSequence() + 1);
    return "TC-" + project.getTestCaseSequence();
  }

  @Transactional
  public ProjectResponse create(UUID workspaceId, ProjectRequest request) {
    workspaces.requireAccess(workspaceId, Permission.CONTENT_EDIT);

    Project project = new Project();
    project.setWorkspaceId(workspaceId);
    project.setKey(resolveKey(workspaceId, request));
    project.setName(request.name().trim());
    project.setDescription(trimToNull(request.description()));
    project.setEngine(request.engine() == null ? AutomationEngine.PLAYWRIGHT : request.engine());
    return mapper.toResponse(repository.save(project));
  }

  @Transactional
  public ProjectResponse update(UUID id, ProjectPatchRequest request) {
    Project project = requireVisible(id, Permission.CONTENT_EDIT);
    if (request.name() != null) {
      project.setName(request.name().trim());
    }
    if (request.description() != null) {
      project.setDescription(trimToNull(request.description()));
    }
    return mapper.toResponse(repository.save(project));
  }

  @Transactional
  public void delete(UUID id) {
    repository.delete(requireVisible(id, Permission.CONTENT_DELETE));
  }

  /** Loads a project only if the caller belongs to its workspace and may do this there. */
  private Project requireVisible(UUID id, Permission permission) {
    Project project = require(id);
    workspaces.requireAccess(project.getWorkspaceId(), permission);
    return project;
  }

  private Project require(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("resource.project", id));
  }

  /**
   * A key the caller chose is theirs: taken, it is a conflict they can act on. A derived one is our
   * guess at a handle, so a collision gets a numeric suffix rather than an error about a field the
   * user never filled in.
   */
  private String resolveKey(UUID workspaceId, ProjectRequest request) {
    if (StringUtils.hasText(request.key())) {
      String requested = request.key().trim().toUpperCase(Locale.ROOT);
      if (repository.existsByWorkspaceIdAndKey(workspaceId, requested)) {
        throw new ConflictException("error.project.duplicate-key", requested);
      }
      return requested;
    }

    String base = deriveKey(request.name());
    String candidate = base;
    for (int suffix = 2; repository.existsByWorkspaceIdAndKey(workspaceId, candidate); suffix++) {
      String tail = String.valueOf(suffix);
      candidate = base.substring(0, Math.min(base.length(), KEY_MAX - tail.length())) + tail;
    }
    return candidate;
  }

  /**
   * The first word of the name, upper-cased and stripped of everything a reference cannot carry —
   * "Acme storefront" becomes ACME. Predictable beats clever here: the user reads this key back to
   * a colleague, and initials of a renamed project would stop matching the name on the screen.
   */
  private static String deriveKey(String name) {
    String slug = Slugs.of(name, KEY_MAX);
    String firstWord = slug.isEmpty() ? "" : slug.split("-", 2)[0];
    String key = firstWord.toUpperCase(Locale.ROOT);
    return key.isEmpty() ? FALLBACK_KEY : key;
  }

  private static String trimToNull(String text) {
    return StringUtils.hasText(text) ? text.trim() : null;
  }
}
