package dev.specra.api.feature.ai.service;

import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ErrorCode;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Turns a failed model call into an answer the caller can act on.
 *
 * <p>Without this, a provider saying "invalid x-api-key" arrives at the client as {@code
 * internal-error}: "Something went wrong on our side." That is false — nothing went wrong on our
 * side — and it is unactionable, so the person who could fix it in thirty seconds instead reads a
 * stack trace. {@code ErrorCode} has had the right codes all along; nothing mapped onto them,
 * because the provider throws a bare {@link RuntimeException} that slips past the handlers keyed on
 * Spring AI's own exception types.
 *
 * <p>So the translation happens here, at the one boundary that knows a model was being called,
 * rather than by guessing in the global handler. Anything thrown inside {@link #guard} was thrown
 * while talking to a provider, and that is what the caller is told.
 */
@Component
public class AiFailures {

  private static final Logger log = LoggerFactory.getLogger(AiFailures.class);

  /**
   * Spring AI's response error handler puts the upstream status into its message and nothing else
   * carries it, so this reads it back. Best-effort by design: a miss costs a slightly less precise
   * code, never a wrong outcome.
   */
  private static final Pattern STATUS = Pattern.compile("Status:\\s*\\[(\\d{3})");

  private final AiCredentials credentials;
  private final AiProviders providers;

  public AiFailures(AiCredentials credentials, AiProviders providers) {
    this.credentials = credentials;
    this.providers = providers;
  }

  /**
   * Refuse before calling out when there is nothing to call with. Cheaper than a round trip, and it
   * produces the one error a user can actually fix.
   */
  public void requireConfigured() {
    if (!credentials.chatIsConfigured()) {
      throw new BusinessException(
          ErrorCode.AI_NOT_CONFIGURED, ErrorCode.AI_NOT_CONFIGURED.detailKey(), providers.chat());
    }
  }

  /** Run a blocking model call with both guards applied. */
  public <T> T guard(Supplier<T> call) {
    requireConfigured();
    try {
      return call.get();
    } catch (BusinessException e) {
      throw e;
    } catch (RuntimeException e) {
      throw translate(e);
    }
  }

  /**
   * The streaming equivalent. The check runs before a subscription exists, so a request with no key
   * fails like any other call instead of opening a stream that can only break.
   */
  public <T> Flux<T> guardStream(Supplier<Flux<T>> call) {
    requireConfigured();
    return Flux.defer(call).onErrorMap(e -> e instanceof BusinessException ? e : translate(e));
  }

  /**
   * Which of the three AI codes fits. The default is {@link ErrorCode#AI_PROVIDER_ERROR} rather
   * than a rethrow: from the caller's side a failed model call is a failed model call, and letting
   * one through to the catch-all is how this became "Something went wrong on our side" in the first
   * place. The original goes to the log in full, so nothing is lost for whoever is debugging.
   */
  BusinessException translate(Throwable failure) {
    ErrorCode code = classify(failure);
    log.warn(
        "Model call to '{}' failed, reporting {}: {}",
        providers.chat(),
        code.slug(),
        failure.toString(),
        failure);
    return new BusinessException(code, code.detailKey(), providers.chat());
  }

  private static ErrorCode classify(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConnectException
          || cause instanceof UnknownHostException
          || cause instanceof TimeoutException
          || cause instanceof IOException) {
        return ErrorCode.AI_PROVIDER_UNAVAILABLE;
      }
      if (cause instanceof org.springframework.ai.retry.TransientAiException) {
        return ErrorCode.AI_PROVIDER_UNAVAILABLE;
      }
      ErrorCode byStatus = fromStatusIn(cause.getMessage());
      if (byStatus != null) {
        return byStatus;
      }
      if (cause.getCause() == cause) {
        break;
      }
    }
    return ErrorCode.AI_PROVIDER_ERROR;
  }

  private static ErrorCode fromStatusIn(String message) {
    if (message == null) {
      return null;
    }
    Matcher matcher = STATUS.matcher(message);
    if (!matcher.find()) {
      return null;
    }
    int status = Integer.parseInt(matcher.group(1));
    if (status == 429) {
      return ErrorCode.RATE_LIMITED;
    }
    return status >= 500 ? ErrorCode.AI_PROVIDER_UNAVAILABLE : ErrorCode.AI_PROVIDER_ERROR;
  }
}
