package dev.specra.api.core.error;

import dev.specra.api.core.i18n.MessageResolver;
import dev.specra.api.core.logging.MdcKeys;
import io.micrometer.tracing.Tracer;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Builds every error body this API returns, as RFC 9457 {@code application/problem+json}.
 *
 * <p>On top of the standard members it always sets four extensions:
 *
 * <ul>
 *   <li>{@code code} — the stable {@link ErrorCode} slug clients branch on
 *   <li>{@code timestamp} — when the failure was rendered
 *   <li>{@code traceId} / {@code requestId} — the same ids that appear in the log line for this
 *       request, so a user can paste one from a support ticket and land on the exact stack trace
 * </ul>
 */
@Component
public class ProblemFactory {

  private final MessageResolver messages;
  private final ObjectProvider<Tracer> tracers;

  public ProblemFactory(MessageResolver messages, ObjectProvider<Tracer> tracers) {
    this.messages = messages;
    this.tracers = tracers;
  }

  /** Title and detail both come from the bundle, in the caller's locale. */
  public ProblemDetail of(ErrorCode code, String detailKey, @Nullable Object... args) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(code.status(), messages.get(detailKey, args));
    decorate(problem, code, null);
    return problem;
  }

  /**
   * Title comes from the bundle; the detail is text the framework or a provider already produced.
   * Used where a translated sentence would lose information the caller needs verbatim.
   */
  public ProblemDetail ofLiteral(ErrorCode code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
    decorate(problem, code, null);
    return problem;
  }

  /**
   * Fills in {@code type}, {@code title}, {@code instance} and the four extensions on a problem
   * that already exists — the shape Spring MVC hands to an exception handler for its own errors.
   */
  public void decorate(ProblemDetail problem, ErrorCode code, @Nullable String path) {
    problem.setType(code.type());
    problem.setTitle(messages.get(code.titleKey()));
    if (problem.getDetail() == null || problem.getDetail().isBlank()) {
      problem.setDetail(messages.get(code.detailKey()));
    }
    if (path != null && problem.getInstance() == null) {
      problem.setInstance(URI.create(path));
    }

    problem.setProperty("code", code.slug());
    problem.setProperty("timestamp", Instant.now());
    putIfPresent(problem, "requestId", MDC.get(MdcKeys.REQUEST_ID));
    putIfPresent(problem, "traceId", currentTraceId());
  }

  private static void putIfPresent(ProblemDetail problem, String name, @Nullable String value) {
    if (value != null && !value.isBlank()) {
      problem.setProperty(name, value);
    }
  }

  /** Attached only for validation failures; the web app keys its form fields off this map. */
  public void addFieldErrors(ProblemDetail problem, Map<String, String> fieldErrors) {
    problem.setProperty("fieldErrors", fieldErrors);
  }

  /**
   * Null when tracing is switched off, which is exactly what the field should say — better than
   * inventing an id that matches nothing in the logs.
   */
  @Nullable private String currentTraceId() {
    Tracer tracer = tracers.getIfAvailable();
    if (tracer == null || tracer.currentSpan() == null) {
      return MDC.get(MdcKeys.TRACE_ID);
    }
    return tracer.currentSpan().context().traceId();
  }
}
