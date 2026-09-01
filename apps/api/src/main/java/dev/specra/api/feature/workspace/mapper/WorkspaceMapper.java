package dev.specra.api.feature.workspace.mapper;

import dev.specra.api.feature.workspace.domain.Workspace;
import dev.specra.api.feature.workspace.domain.WorkspaceMember;
import dev.specra.api.feature.workspace.domain.WorkspaceRole;
import dev.specra.api.feature.workspace.dto.MemberResponse;
import dev.specra.api.feature.workspace.dto.WorkspaceResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Response only. The slug on the way in is derived or checked before anything is written, which is
 * a rule rather than a field copy, so {@code WorkspaceService} builds the entity itself.
 */
@Mapper(componentModel = "spring")
public interface WorkspaceMapper {

  @Mapping(target = "role", ignore = true)
  WorkspaceResponse toResponse(Workspace workspace);

  /**
   * The same workspace, carrying the caller's role.
   *
   * <p>Written by hand because the role does not come from the entity: it is a fact about who is
   * asking, and a generated two-source mapping would read as though the workspace held it.
   */
  default WorkspaceResponse toResponse(Workspace workspace, WorkspaceRole role) {
    return new WorkspaceResponse(
        workspace.getId(),
        workspace.getName(),
        workspace.getSlug(),
        role,
        workspace.getCreatedAt(),
        workspace.getUpdatedAt());
  }

  @Mapping(target = "userId", source = "user.id")
  @Mapping(target = "email", source = "user.email")
  @Mapping(target = "displayName", source = "user.displayName")
  MemberResponse toResponse(WorkspaceMember member);
}
