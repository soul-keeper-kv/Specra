package dev.specra.api.core.runner;

import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ErrorCode;

/**
 * The runner could not be reached, or did not answer in time.
 *
 * <p>Distinct from a refusal on purpose: this one is ours to fix, and the user can only wait or
 * tell somebody. A refusal is about their test case, and says so.
 */
public class RunnerUnavailableException extends BusinessException {

  public RunnerUnavailableException(Throwable cause) {
    super(ErrorCode.RUNNER_UNAVAILABLE, ErrorCode.RUNNER_UNAVAILABLE.detailKey());
    initCause(cause);
  }
}
