package dev.specra.api.core.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * One access-log line per request: method, path, status, duration.
 *
 * <p>Deliberately not {@code CommonsRequestLoggingFilter} — that one buffers the payload in order
 * to log it, which breaks the SSE endpoint and would put user text and prompts into the log.
 * Nothing here touches the body.
 *
 * <p>Health checks and API docs are skipped: a container probing {@code /actuator/health} every few
 * seconds drowns out the traffic anyone actually wants to read.
 *
 * <p>Registered in {@code ObservabilityConfig} rather than annotated {@code @Component}, because
 * its two settings are bound from {@code specra.logging.access.*}.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

  private static final List<String> IGNORED_PREFIXES =
      List.of("/actuator", "/v3/api-docs", "/swagger-ui", "/favicon.ico");

  private final boolean enabled;
  private final long slowRequestMillis;

  public RequestLoggingFilter(boolean enabled, long slowRequestMillis) {
    this.enabled = enabled;
    this.slowRequestMillis = slowRequestMillis;
  }

  @Override
  protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
    if (!enabled) {
      return true;
    }
    String path = request.getRequestURI();
    return IGNORED_PREFIXES.stream().anyMatch(path::startsWith);
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain chain)
      throws ServletException, IOException {

    long startedAt = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      long millis = (System.nanoTime() - startedAt) / 1_000_000;
      String query = request.getQueryString();
      String path = query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
      int status = response.getStatus();

      // A slow 200 is as much of a problem as a fast 500; both deserve to stand out in the log.
      if (status >= 500 || millis >= slowRequestMillis) {
        log.warn("{} {} -> {} in {}ms", request.getMethod(), path, status, millis);
      } else {
        log.info("{} {} -> {} in {}ms", request.getMethod(), path, status, millis);
      }
    }
  }
}
