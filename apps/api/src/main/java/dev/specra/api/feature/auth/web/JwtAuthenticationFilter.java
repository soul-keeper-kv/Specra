package dev.specra.api.feature.auth.web;

import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ErrorCode;
import dev.specra.api.core.security.AuthenticatedUser;
import dev.specra.api.feature.auth.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns {@code Authorization: Bearer …} into an authenticated {@code SecurityContext}.
 *
 * <p>Verification is a signature check and nothing else — no database round trip, which is the
 * whole reason the access token is a JWT. The authority granted is a flat {@code ROLE_USER}: being
 * signed in is the only thing a token proves. What a user may do is per workspace and is read from
 * the membership row at the point of the decision, by {@code WorkspaceAccess}.
 *
 * <p>A token that fails to verify does not fail the request here. It clears the context, records
 * why on the request, and carries on — so a public endpoint still answers, and a protected one is
 * refused by {@link ProblemAuthenticationEntryPoint}, which reads that record and can tell the
 * client "your token expired, refresh it" rather than the far less useful "sign in".
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  /** Where the entry point looks for the reason this request has no authentication. */
  public static final String FAILURE_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".error";

  private static final String BEARER_PREFIX = "Bearer ";

  /** Signed in, and that is all. Everything finer-grained is a workspace membership. */
  private static final List<SimpleGrantedAuthority> AUTHORITIES =
      List.of(new SimpleGrantedAuthority("ROLE_USER"));

  private final TokenService tokens;

  public JwtAuthenticationFilter(TokenService tokens) {
    this.tokens = tokens;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain chain)
      throws ServletException, IOException {

    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      authenticate(request, header.substring(BEARER_PREFIX.length()).trim());
    }
    chain.doFilter(request, response);
  }

  private void authenticate(HttpServletRequest request, String token) {
    try {
      AuthenticatedUser user = tokens.verify(token);
      var authentication = new UsernamePasswordAuthenticationToken(user, null, AUTHORITIES);
      authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
      SecurityContextHolder.getContext().setAuthentication(authentication);
    } catch (BusinessException e) {
      SecurityContextHolder.clearContext();
      request.setAttribute(FAILURE_ATTRIBUTE, ErrorCode.INVALID_TOKEN);
    }
  }
}
