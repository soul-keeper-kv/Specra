package dev.specra.api.feature.workspace.dto;

import dev.specra.api.feature.workspace.domain.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A role and what it can do, so the UI can grey out a button for the same reason the API would
 * refuse it — rather than by hard-coding a second copy of the rules in TypeScript.
 *
 * @param permissions kebab-case slugs, the stable names a client may match on
 */
public record RoleResponse(
    WorkspaceRole role,
    @Schema(example = "[\"workspace-view\",\"member-view\",\"content-view\",\"content-edit\"]")
        List<String> permissions) {

  public static RoleResponse of(WorkspaceRole role) {
    return new RoleResponse(
        role, role.permissions().stream().map(permission -> permission.slug()).sorted().toList());
  }
}
