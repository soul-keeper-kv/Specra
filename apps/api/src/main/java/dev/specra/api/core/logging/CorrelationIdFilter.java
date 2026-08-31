package dev.specra.api.core.logging;

import dev.specra.api.core.i18n.HttpLocaleResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts the ids every log line of a request is tagged with into the {@link MDC}, and echoes them
 * back to the caller.
 *
 * <p>{@code traceId} and {@code spanId} come from Micrometer Tracing and identify the call across
 * services. {@code requestId} is this hop only, and is accepted from the caller so the web app can
 * generate one, show it in an error toast, and have a support ticket point straight at the right
 * log lines. A caller-supplied value is length- and character-checked before it is used, because it
 * is about to be written into log output.
 *
 * <p>Runs first, so nothing downstream — including the request log and the exception handler — can
 * emit a line without these fields.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

  /** Anything outside this is discarded and replaced: a log field must not carry injected text. */
  private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain chain)
      throws ServletException, IOException {

    String requestId = sanitise(request.getHeader(MdcKeys.REQUEST_ID_HEADER));

    MDC.put(MdcKeys.REQUEST_ID, requestId);
    MDC.put(MdcKeys.HTTP_METHOD, request.getMethod());
    MDC.put(MdcKeys.HTTP_PATH, request.getRequestURI());
    MDC.put(MdcKeys.CLIENT_IP, clientIp(request));
    MDC.put(MdcKeys.LOCALE, HttpLocaleResolver.resolve(request).tag());

    response.setHeader(MdcKeys.REQUEST_ID_HEADER, requestId);

    try {
      chain.doFilter(request, response);
    } finally {
      // The trace id is only known once Micrometer has opened the observation, which happens
      // downstream of this filter — so it is read on the way out, not on the way in.
      String traceId = MDC.get(MdcKeys.TRACE_ID);
      if (traceId != null && !response.isCommitted()) {
        response.setHeader(MdcKeys.TRACE_ID_HEADER, traceId);
      }
      MDC.remove(MdcKeys.REQUEST_ID);
      MDC.remove(MdcKeys.HTTP_METHOD);
      MDC.remove(MdcKeys.HTTP_PATH);
      MDC.remove(MdcKeys.CLIENT_IP);
      MDC.remove(MdcKeys.LOCALE);
    }
  }

  private static String sanitise(@Nullable String candidate) {
    return candidate != null && SAFE_ID.matcher(candidate).matches()
        ? candidate
        : UUID.randomUUID().toString();
  }

  /**
   * Only the first hop of {@code X-Forwarded-For} is meaningful, and only behind a proxy that is
   * known to rewrite the header. Logged for support, never used for authorisation.
   */
  private static String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded == null || forwarded.isBlank()) {
      return String.valueOf(request.getRemoteAddr());
    }
    int comma = forwarded.indexOf(',');
    return (comma == -1 ? forwarded : forwarded.substring(0, comma)).trim();
  }
}
