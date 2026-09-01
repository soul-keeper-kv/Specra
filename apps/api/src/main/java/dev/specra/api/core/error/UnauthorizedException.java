package dev.specra.api.core.error;

/**
 * Thrown when a request needs a signed-in user and does not have one.
 *
 * <p>Distinct from {@link ForbiddenException}: this one says "tell us who you are", and the web app
 * answers it by refreshing its token or sending the user to sign in. A 403 means the identity is
 * known and still not enough, so retrying with the same credentials is pointless.
 */
public class UnauthorizedException extends BusinessException {

  public UnauthorizedException() {
    this(ErrorCode.UNAUTHORIZED.detailKey());
  }

  public UnauthorizedException(String messageKey, Object... messageArgs) {
    super(ErrorCode.UNAUTHORIZED, messageKey, messageArgs);
  }

  /** For the codes that are 401 without being the generic "not signed in" case. */
  public UnauthorizedException(ErrorCode errorCode, String messageKey, Object... messageArgs) {
    super(errorCode, messageKey, messageArgs);
  }
}
