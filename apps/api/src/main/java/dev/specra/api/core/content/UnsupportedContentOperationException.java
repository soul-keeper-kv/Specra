package dev.specra.api.core.content;

import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ErrorCode;

/**
 * The store exists but does not do this — a read-only archive asked to delete, for instance.
 *
 * <p>Distinct from a 404 and from a validation failure: nothing about the request was malformed,
 * the capability simply is not there. The kind and the capability are passed as message arguments
 * so they land in the log line, while the sentence the caller sees stays translated and generic.
 */
public class UnsupportedContentOperationException extends BusinessException {

  public UnsupportedContentOperationException(String kind, ContentCapability capability) {
    super(
        ErrorCode.UNSUPPORTED_OPERATION,
        ErrorCode.UNSUPPORTED_OPERATION.detailKey(),
        kind,
        capability);
  }
}
