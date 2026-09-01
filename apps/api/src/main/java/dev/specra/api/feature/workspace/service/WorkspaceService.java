package dev.specra.api.feature.workspace.service;

import dev.specra.api.core.error.ConflictException;
import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.core.text.Slugs;
import dev.specra.api.core.web.PageResponse;
import dev.specra.api.feature.workspace.domain.Workspace;
import dev.specra.api.feature.workspace.domain.WorkspaceRepository;
import dev.specra.api.feature.workspace.dto.WorkspaceRequest;
import dev.specra.api.feature.workspace.dto.WorkspaceResponse;
import dev.specra.api.feature.workspace.mapper.WorkspaceMapper;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** The tenant: created, listed, and asked whether it exists by every feature underneath it. */
@Service
@Transactional(readOnly = true)
public class WorkspaceService {

  /** What a name made entirely of characters no slug can carry falls back to. */
  private static final String FALLBACK_SLUG = "workspace";

  private static final int SLUG_MAX = 64;

  private final WorkspaceRepository repository;
  private final WorkspaceMapper mapper;

  public WorkspaceService(WorkspaceRepository repository, WorkspaceMapper mapper) {
    this.repository = repository;
    this.mapper = mapper;
  }

  public PageResponse<WorkspaceResponse> list(Pageable pageable) {
    return PageResponse.from(repository.findAll(pageable), mapper::toResponse);
  }

  public WorkspaceResponse get(UUID id) {
    return mapper.toResponse(require(id));
  }

  /**
   * The one throw site for a workspace that is not there.
   *
   * <p>Public because it is how another feature checks its parent — {@code ProjectService} calls it
   * rather than reaching for {@code WorkspaceRepository}, so the entity never crosses a feature
   * boundary and the 404 reads the same wherever it came from.
   */
  public void requireExists(UUID id) {
    if (!repository.existsById(id)) {
      throw new ResourceNotFoundException("resource.workspace", id);
    }
  }

  @Transactional
  public WorkspaceResponse create(WorkspaceRequest request) {
    Workspace workspace = new Workspace();
    workspace.setName(request.name().trim());
    workspace.setSlug(resolveSlug(request));
    return mapper.toResponse(repository.save(workspace));
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
