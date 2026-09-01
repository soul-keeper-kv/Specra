package dev.specra.api.core.content;

import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ErrorCode;
import java.util.Collection;

/**
 * A caller asked for a store that is not registered.
 *
 * <p>The known kinds go into the message on purpose: when the caller is a model, naming the valid
 * options is what lets it correct itself on the next turn instead of looping.
 */
public class UnknownContentKindException extends BusinessException {

  public UnknownContentKindException(String requested, Collection<String> known) {
    super(
        ErrorCode.INVALID_PARAMETER,
        "error.content.unknown-kind",
        requested,
        String.join(", ", known));
  }
}
