package dev.specra.api.feature.auth.service;

import java.util.UUID;

/**
 * What the auth feature announces, for whoever cares.
 *
 * <p>The point is the direction of the arrow. A new account needs a workspace to land in, but auth
 * knowing about workspaces would make the two inseparable — and would mean an installation that
 * only ever authenticates could not drop the tenancy code. So auth says what happened and stops;
 * {@code WorkspaceProvisioning} listens, {@code AFTER_COMMIT}, and creates the workspace.
 */
public final class AuthEvents {

  private AuthEvents() {}

  /**
   * A person now has an account. Carries what a listener needs in order to act without loading the
   * row, which is what lets the listener run after the registering transaction has committed.
   */
  public record UserRegistered(UUID userId, String displayName) {}
}
