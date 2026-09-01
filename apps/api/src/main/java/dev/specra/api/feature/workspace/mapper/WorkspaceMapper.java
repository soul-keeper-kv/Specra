package dev.specra.api.feature.workspace.mapper;

import dev.specra.api.feature.workspace.domain.Workspace;
import dev.specra.api.feature.workspace.dto.WorkspaceResponse;
import org.mapstruct.Mapper;

/**
 * Response only. The slug on the way in is derived or checked before anything is written, which is
 * a rule rather than a field copy, so {@code WorkspaceService} builds the entity itself.
 */
@Mapper(componentModel = "spring")
public interface WorkspaceMapper {

  WorkspaceResponse toResponse(Workspace workspace);
}
