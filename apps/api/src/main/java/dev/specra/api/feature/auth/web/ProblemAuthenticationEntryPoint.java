package dev.specra.api.feature.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.specra.api.core.error.ErrorCode;
import dev.specra.api.core.error.ProblemFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Renders the two refusals that happen in the filter chain, before any controller runs.
 *
 * <p>Without this they would come back as Spring Security's empty 401 and 403, which are the only
 * responses in the whole API that are not RFC 9457 — and the web app branches on {@code code}, so
 * an empty body reads to it as an unknown failure rather than as "sign in". The bodies are built by
 * the same {@link ProblemFactory} every other error uses, so they carry the same translated title,
 * the same {@code traceId} and the same {@code requestId}.
 *
 * <p>The 401 distinguishes two cases the client acts on differently: no credentials at all, and
 * credentials that did not verify — {@link JwtAuthenticationFilter} leaves the second on the
 * request, and it is what tells the web app to try its refresh token before giving up.
 */
@Component
public class ProblemAuthenticationEntryPoint
    implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final ProblemFactory problems;
  private final ObjectMapper json;

  public ProblemAuthenticationEntryPoint(ProblemFactory problems, ObjectMapper json) {
    this.problems = problems;
    this.json = json;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException failure)
      throws IOException {
    Object recorded = request.getAttribute(JwtAuthenticationFilter.FAILURE_ATTRIBUTE);
    ErrorCode code = recorded instanceof ErrorCode error ? error : ErrorCode.UNAUTHORIZED;
    write(request, response, code);
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException denied)
      throws IOException {
    write(request, response, ErrorCode.FORBIDDEN);
  }

  private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code)
      throws IOException {
    ProblemDetail problem = problems.of(code, code.detailKey());
    problems.decorate(problem, code, request.getRequestURI());

    response.setStatus(code.status().value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    json.writeValue(response.getOutputStream(), problem);
  }
}
