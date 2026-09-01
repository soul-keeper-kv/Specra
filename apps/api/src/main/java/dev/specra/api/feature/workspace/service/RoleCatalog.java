package dev.specra.api.feature.workspace.service;

import dev.specra.api.feature.workspace.domain.WorkspaceRole;
import dev.specra.api.feature.workspace.dto.RoleResponse;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The roles this installation has, and what each one carries.
 *
 * <p>A service for a constant looks like ceremony, and is not: {@link WorkspaceRole} is a domain
 * type, and the layering here says a controller reaches the domain through the service layer or not
 * at all. That rule is what stops an entity from being serialised straight out of a request
 * handler, and it does not get an exception for the easy case.
 *
 * <p>The list is computed once. It cannot change without a deployment, so recomputing it per
 * request would be work in exchange for nothing.
 */
@Service
public class RoleCatalog {

  private static final List<RoleResponse> ROLES =
      Arrays.stream(WorkspaceRole.values()).map(RoleResponse::of).toList();

  /** Most senior first, matching the declaration order the rank checks read. */
  public List<RoleResponse> all() {
    return ROLES;
  }
}
