package dev.specra.api.common;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest req) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiError.of(404, "Not Found", ex.getMessage(), req.getRequestURI()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest req) {
    Map<String, String> fields = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
    return ResponseEntity.badRequest()
        .body(
            new ApiError(
                Instant.now(),
                400,
                "Bad Request",
                "Validation failed",
                req.getRequestURI(),
                fields));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiError> handleIllegalArgument(
      IllegalArgumentException ex, HttpServletRequest req) {
    return ResponseEntity.badRequest()
        .body(ApiError.of(400, "Bad Request", ex.getMessage(), req.getRequestURI()));
  }

  /**
   * AI providers fail in ways the caller cannot fix (missing key, quota, model down). Surface them
   * as 502 rather than a generic 500 so the UI can say "the model is unavailable".
   */
  @ExceptionHandler(org.springframework.ai.retry.NonTransientAiException.class)
  public ResponseEntity<ApiError> handleAi(
      org.springframework.ai.retry.NonTransientAiException ex, HttpServletRequest req) {
    log.warn("AI provider rejected the request: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(ApiError.of(502, "Bad Gateway", "AI provider error: " + ex.getMessage(),
            req.getRequestURI()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleAnythingElse(Exception ex, HttpServletRequest req) {
    log.error("Unhandled exception on {}", req.getRequestURI(), ex);
    return ResponseEntity.internalServerError()
        .body(
            ApiError.of(
                500, "Internal Server Error", "Unexpected error", req.getRequestURI()));
  }
}
