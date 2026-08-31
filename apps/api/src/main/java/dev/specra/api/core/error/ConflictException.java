package dev.specra.api.core.error;

/** Thrown when the request is well-formed but clashes with the current state of the resource. */
public class ConflictException extends BusinessException {

  public ConflictException(String messageKey, Object... messageArgs) {
    super(ErrorCode.CONFLICT, messageKey, messageArgs);
  }
}
