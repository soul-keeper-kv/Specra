package dev.specra.api.core.error;

/**
 * Thrown when the caller is known and still not allowed to do this.
 *
 * <p>Distinct from {@link UnauthorizedException}: a 401 says "tell us who you are" and is worth
 * retrying with a fresh token, a 403 says the identity is known and insufficient, so retrying
 * cannot help. Callers pass a message key that names what was missing — "you need Manage members in
 * this workspace" is a sentence a user can act on, where a bare "Forbidden" sends them to support.
 */
public class ForbiddenException extends BusinessException {

  public ForbiddenException(String messageKey, Object... messageArgs) {
    super(ErrorCode.FORBIDDEN, messageKey, messageArgs);
  }
}
