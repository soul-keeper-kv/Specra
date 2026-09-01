package dev.specra.api.feature.workspace.web;

import dev.specra.api.feature.workspace.dto.RoleResponse;
import dev.specra.api.feature.workspace.service.RoleCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The roles a workspace has, and what each one can do.
 *
 * <p>It exists so the UI does not have to carry a second copy of the rules. A screen that greys out
 * "Remove" for a member is making the same decision the API makes; reading it from here means the
 * two cannot drift, and adding a permission changes one file rather than two code bases.
 */
@RestController
@RequestMapping("/api/v1/roles")
@Tag(name = "Roles", description = "The workspace roles and the permissions each one carries.")
public class RoleController {

  private final RoleCatalog roles;

  public RoleController(RoleCatalog roles) {
    this.roles = roles;
  }

  @GetMapping
  @Operation(summary = "Every workspace role, most senior first, with its permissions")
  public List<RoleResponse> list() {
    return roles.all();
  }
}
