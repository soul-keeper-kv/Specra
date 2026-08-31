package dev.specra.api.core.error;

import dev.specra.api.core.i18n.MessageResolver;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every exception that escapes a controller into one {@code application/problem+json} body.
 *
 * <p>It extends {@link ResponseEntityExceptionHandler} so Spring MVC's own failures — unreadable
 * JSON, wrong method, unknown path, a query parameter that will not convert — arrive here too
 * instead of falling through to the container's error page. Those already carry a {@link
 * ProblemDetail}; {@link #handleExceptionInternal} only adds the parts that make it ours: the
 * {@code code}, and the trace and request ids that tie the body to a log line.
 *
 * <p>Extending {@code ResponseEntityExceptionHandler} also makes this class {@code
 * MessageSourceAware}, which is what lets Spring resolve its built-in {@code problemDetail.*} keys
 * from the {@code i18n/messages} bundle — that is where the Vietnamese wording of framework errors
 * comes from.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final ProblemFactory problems;
  private final MessageResolver messages;

  public GlobalExceptionHandler(ProblemFactory problems, MessageResolver messages) {
    this.problems = problems;
    this.messages = messages;
  }

  // ── application failures ───────────────────────────────────────────────────

  @ExceptionHandler(BusinessException.class)
  public ProblemDetail handleBusiness(BusinessException ex) {
    log.debug("{} -> {}", ex.getClass().getSimpleName(), ex.messageKey());
    return problems.of(ex.errorCode(), ex.messageKey(), ex.messageArgs());
  }

  /**
   * Validation on a method parameter rather than a request body — {@code @Validated} on a service,
   * or a constrained query parameter. Reported with the same shape as body validation so a client
   * has one code path for both.
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
    Map<String, String> fields = new LinkedHashMap<>();
    for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
      fields.putIfAbsent(lastPathNode(violation), violation.getMessage());
    }
    ProblemDetail problem =
        problems.of(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.detailKey());
    problems.addFieldErrors(problem, fields);
    return problem;
  }

  /** A concurrent write won; {@code @Version} on the entity is what detects it. */
  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
    log.info("Optimistic lock conflict: {}", ex.getMessage());
    return problems.of(ErrorCode.CONFLICT, ErrorCode.CONFLICT.detailKey());
  }

  /**
   * A unique or foreign-key constraint refused the write. The database's own message names columns
   * and constraints, so it is logged and not returned.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
    log.warn("Data integrity violation", ex);
    return problems.of(ErrorCode.CONFLICT, ErrorCode.CONFLICT.detailKey());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
    return problems.ofLiteral(ErrorCode.INVALID_PARAMETER, ex.getMessage());
  }

  // ── model providers ────────────────────────────────────────────────────────

  /**
   * The provider answered and said no: missing key, exhausted quota, unknown model. Retrying will
   * not help, so it is a 502 and the cause goes to the log rather than to the caller.
   */
  @ExceptionHandler(org.springframework.ai.retry.NonTransientAiException.class)
  public ProblemDetail handleNonTransientAi(
      org.springframework.ai.retry.NonTransientAiException ex) {
    log.warn("Model provider rejected the request: {}", ex.getMessage());
    return problems.of(ErrorCode.AI_PROVIDER_ERROR, ErrorCode.AI_PROVIDER_ERROR.detailKey());
  }

  /** Timed out or unreachable. Retrying may well help, hence 503 rather than 502. */
  @ExceptionHandler(org.springframework.ai.retry.TransientAiException.class)
  public ProblemDetail handleTransientAi(org.springframework.ai.retry.TransientAiException ex) {
    log.warn("Model provider unreachable: {}", ex.getMessage());
    return problems.of(
        ErrorCode.AI_PROVIDER_UNAVAILABLE, ErrorCode.AI_PROVIDER_UNAVAILABLE.detailKey());
  }

  // ── last resort ────────────────────────────────────────────────────────────

  /**
   * Anything unforeseen. The stack trace goes to the log with this request's trace id attached; the
   * caller gets a generic sentence plus that same id, and nothing about our internals.
   */
  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex, WebRequest request) {
    log.error("Unhandled exception on {}", pathOf(request), ex);
    return problems.of(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.detailKey());
  }

  // ── Spring MVC's own exceptions ────────────────────────────────────────────

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      @NonNull MethodArgumentNotValidException ex,
      @NonNull HttpHeaders headers,
      @NonNull HttpStatusCode status,
      @NonNull WebRequest request) {
    Map<String, String> fields = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(
            error -> fields.putIfAbsent(error.getField(), describe(error.getDefaultMessage())));
    ex.getBindingResult()
        .getGlobalErrors()
        .forEach(
            error ->
                fields.putIfAbsent(error.getObjectName(), describe(error.getDefaultMessage())));

    ProblemDetail problem = ex.getBody();
    problems.addFieldErrors(problem, fields);
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  /**
   * Spring 6.1 raises this, rather than {@code ConstraintViolationException}, when the constraint
   * sits on a controller method parameter.
   */
  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      @NonNull HandlerMethodValidationException ex,
      @NonNull HttpHeaders headers,
      @NonNull HttpStatusCode status,
      @NonNull WebRequest request) {
    Map<String, String> fields = new LinkedHashMap<>();
    ex.getParameterValidationResults()
        .forEach(
            result -> {
              String name = result.getMethodParameter().getParameterName();
              result
                  .getResolvableErrors()
                  .forEach(
                      error ->
                          fields.putIfAbsent(
                              name == null ? "request" : name,
                              describe(error.getDefaultMessage())));
            });

    ProblemDetail problem = ex.getBody();
    problems.addFieldErrors(problem, fields);
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  /**
   * The single decoration point: everything {@link ResponseEntityExceptionHandler} handles passes
   * through here, so the {@code code}, the ids and the localised title are applied once instead of
   * in a dozen overrides.
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      @NonNull Exception ex,
      @Nullable Object body,
      @NonNull HttpHeaders headers,
      @NonNull HttpStatusCode status,
      @NonNull WebRequest request) {

    ProblemDetail problem =
        body instanceof ProblemDetail existing ? existing : ProblemDetail.forStatus(status);

    problems.decorate(problem, codeFor(ex, status), pathOf(request));

    if (status.is5xxServerError()) {
      log.error("{} on {}", ex.getClass().getSimpleName(), pathOf(request), ex);
    } else {
      log.debug("{} on {}: {}", ex.getClass().getSimpleName(), pathOf(request), ex.getMessage());
    }

    return super.handleExceptionInternal(ex, problem, headers, status, request);
  }

  /**
   * Maps a framework exception to our vocabulary. A {@code switch} over types would read better but
   * needs Java 21 patterns, and this project targets 17.
   */
  private static ErrorCode codeFor(Exception ex, HttpStatusCode status) {
    if (ex instanceof MethodArgumentNotValidException
        || ex instanceof HandlerMethodValidationException) {
      return ErrorCode.VALIDATION_FAILED;
    }
    if (ex instanceof HttpMessageNotReadableException) {
      return ErrorCode.MALFORMED_REQUEST;
    }
    if (ex instanceof MissingServletRequestParameterException) {
      return ErrorCode.MISSING_PARAMETER;
    }
    if (ex instanceof TypeMismatchException) {
      return ErrorCode.INVALID_PARAMETER;
    }
    if (ex instanceof NoResourceFoundException || ex instanceof NoHandlerFoundException) {
      return ErrorCode.ENDPOINT_NOT_FOUND;
    }
    if (ex instanceof HttpRequestMethodNotSupportedException) {
      return ErrorCode.METHOD_NOT_ALLOWED;
    }
    if (ex instanceof HttpMediaTypeNotSupportedException) {
      return ErrorCode.UNSUPPORTED_MEDIA_TYPE;
    }
    return ErrorCode.forStatus(status);
  }

  /**
   * Bean Validation has already interpolated the message against our bundle, so it is normally
   * final. The lookup is a safety net for a message that is still a bare key.
   */
  private String describe(@Nullable String defaultMessage) {
    if (defaultMessage == null || defaultMessage.isBlank()) {
      return messages.get(ErrorCode.VALIDATION_FAILED.detailKey());
    }
    return messages.getOrDefault(defaultMessage, defaultMessage);
  }

  private static String lastPathNode(ConstraintViolation<?> violation) {
    String path = violation.getPropertyPath().toString();
    int dot = path.lastIndexOf('.');
    return dot == -1 ? path : path.substring(dot + 1);
  }

  @Nullable private static String pathOf(WebRequest request) {
    return request instanceof ServletWebRequest servlet
        ? servlet.getRequest().getRequestURI()
        : null;
  }
}
