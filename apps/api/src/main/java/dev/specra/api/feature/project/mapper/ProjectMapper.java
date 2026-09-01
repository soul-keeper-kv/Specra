package dev.specra.api.feature.project.mapper;

import dev.specra.api.feature.project.domain.Project;
import dev.specra.api.feature.project.dto.ProjectResponse;
import org.mapstruct.Mapper;

/**
 * Response only. Creating a project derives a key and patching one deliberately ignores most of
 * what it is sent, and neither is a field copy, so {@code ProjectService} does both by hand.
 */
@Mapper(componentModel = "spring")
public interface ProjectMapper {

  ProjectResponse toResponse(Project project);
}
