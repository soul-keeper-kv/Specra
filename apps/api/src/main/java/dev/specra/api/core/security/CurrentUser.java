package dev.specra.api.core.security;

import dev.specra.api.core.error.UnauthorizedException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Reads the caller off the security context.
 *
 * <p>A service asks this rather than taking a user id parameter it would have to trust: the id is
 * then always the authenticated one, and no controller can pass somebody else's by accident. Static
 * because {@code SecurityContextHolder} is already a thread-local — wrapping it in a bean would add
 * an injection point without adding a seam, since a test sets the context either way.
 */
public final class CurrentUser {

  private CurrentUser() {}

  public static Optional<AuthenticatedUser> optional() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
      return Optional.empty();
    }
    return Optional.of(user);
  }

  /**
   * For code that is only reachable behind authentication; the throw is a bug report, not a flow.
   */
  public static AuthenticatedUser require() {
    return optional().orElseThrow(UnauthorizedException::new);
  }

  public static UUID requireId() {
    return require().id();
  }
}
