package dev.specra.api.core.security;

import java.util.UUID;

/**
 * Who is making this request, as far as the access token says.
 *
 * <p>Identity only, and no roles: a workspace role is per workspace and changes without the token
 * changing, so it is resolved from the database at the point of the decision rather than carried
 * here and trusted until the token expires. See {@code WorkspaceGuard}.
 *
 * <p>This is the principal of the {@code Authentication} the JWT filter installs, which is why it
 * lives in {@code core}: a service reads it without knowing which feature authenticated the caller.
 */
public record AuthenticatedUser(UUID id, String email, String displayName) {}
