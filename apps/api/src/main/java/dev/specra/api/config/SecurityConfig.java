package dev.specra.api.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import dev.specra.api.feature.auth.web.JwtAuthenticationFilter;
import dev.specra.api.feature.auth.web.ProblemAuthenticationEntryPoint;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import jakarta.servlet.DispatcherType;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Who may reach what, and the pieces that make a token verifiable.
 *
 * <p>Three decisions shape the chain. It is <b>stateless</b>: there is no cookie session, so CSRF
 * has nothing to protect and is off — a bearer token is not sent by the browser on its own, which
 * is the property CSRF defends against. It is <b>closed by default</b>: {@code /api/**} needs a
 * token, and the handful of endpoints that cannot ({@code register}, {@code login}, {@code
 * refresh}) are listed one by one, so a new endpoint is protected by forgetting rather than exposed
 * by it. And every refusal is an <b>RFC 9457 problem document</b>, because the web app branches on
 * {@code code} and Spring Security's default empty 401 says nothing.
 *
 * <p>Authorisation is not here. A role in Specra is per workspace, held on a membership row, so it
 * is decided where the workspace is known — {@code WorkspaceAccess} — and not by a URL pattern.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

  private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

  /** Must match the default in {@code application.yml}; used only to warn that it is in use. */
  private static final String DEVELOPMENT_SECRET = "dev-only-secret-not-for-anything-that-matters";

  private static final String[] PUBLIC_ENDPOINTS = {
    "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh"
  };

  /**
   * Artifact downloads, which carry their own authorisation in the URL.
   *
   * <p>Public to Spring Security, not public in fact: the link is HMAC-signed over its key and its
   * expiry, and the controller refuses anything unsigned or expired. It has to be reachable without
   * the Authorization header because a browser fetching a video or a trace — in a &lt;video&gt;
   * tag, in a new tab, through the trace viewer — sends no header at all, and proxying the bytes
   * through an authenticated endpoint is exactly what 06-execution.md rules out.
   */
  private static final String[] SIGNED_ENDPOINTS = {"/api/v1/artifacts"};

  /**
   * The OpenAPI document and its UI. Public because a client generates its types from it before it
   * has an account, and because the document describes the shape of the API rather than its data.
   */
  private static final String[] PUBLIC_DOCS = {
    "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
  };

  private final SpecraProperties.Security config;

  public SecurityConfig(SpecraProperties properties) {
    this.config = properties.security();
  }

  @Bean
  SecurityFilterChain apiSecurity(
      HttpSecurity http, JwtAuthenticationFilter jwt, ProblemAuthenticationEntryPoint problems)
      throws Exception {

    http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(
            sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // No form login and no HTTP Basic: both would answer a browser with a prompt, and the
        // only client here is a fetch() that needs a JSON body it can read.
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .exceptionHandling(
            handling -> handling.authenticationEntryPoint(problems).accessDeniedHandler(problems))
        .authorizeHttpRequests(
            requests ->
                requests
                    // Authorisation happens on the initial dispatch. The container re-enters the
                    // chain when a streaming response resumes (ASYNC) and when an error is
                    // rendered (ERROR), and by then the SecurityContext has been cleared — so
                    // deciding again would deny the second half of every SSE stream, mid-body,
                    // with the response already committed and nowhere to put the 401. Nothing
                    // outside the container can start either dispatch.
                    .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(PUBLIC_ENDPOINTS)
                    .permitAll()
                    .requestMatchers(SIGNED_ENDPOINTS)
                    .permitAll()
                    .requestMatchers(PUBLIC_DOCS)
                    .permitAll()
                    // Liveness for a load balancer, and the build's identity. Everything else
                    // actuator exposes is operational data — and /actuator/loggers accepts a
                    // POST that changes log levels, which is not a thing to leave open.
                    .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/actuator/**")
                    .authenticated()
                    .requestMatchers("/api/**")
                    .authenticated()
                    // Anything outside the API surface — the error dispatch, static resources —
                    // is left alone; NoResourceFoundException still renders as problem+json.
                    .anyRequest()
                    .permitAll())
        .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  /**
   * Delegating, so the hash carries its own algorithm as a prefix. Today every password is bcrypt;
   * the day that has to change, existing hashes keep verifying and new ones are written with the
   * new algorithm, with no migration and no flag day.
   */
  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  JwtEncoder jwtEncoder() {
    warnIfDevelopmentSecret();
    return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey()));
  }

  /**
   * Verifies the signature, the expiry and the issuer. The issuer check is what stops a token
   * minted by something else that happens to share the secret from being accepted here.
   */
  @Bean
  JwtDecoder jwtDecoder() {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(signingKey()).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(config.jwt().issuer()));
    return decoder;
  }

  /**
   * One clock for everything that expires. Injected rather than called statically so a test can
   * move time forward instead of sleeping through a lockout.
   */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /** Puts the bearer scheme on every operation, so "Authorize" in the UI actually works. */
  @Bean
  OpenApiCustomizer securitySchemeCustomizer() {
    return openApi -> {
      Components components =
          openApi.getComponents() == null ? new Components() : openApi.getComponents();
      components.addSecuritySchemes(
          "bearerAuth",
          new SecurityScheme()
              .type(SecurityScheme.Type.HTTP)
              .scheme("bearer")
              .bearerFormat("JWT")
              .description(
                  "The accessToken from /api/v1/auth/login. Paste the token itself; the UI adds "
                      + "the Bearer prefix."));
      openApi.setComponents(components);
      openApi.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    };
  }

  private SecretKeySpec signingKey() {
    return new SecretKeySpec(config.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  private void warnIfDevelopmentSecret() {
    if (DEVELOPMENT_SECRET.equals(config.jwt().secret())) {
      log.warn(
          "Signing access tokens with the built-in development secret. Anyone who has read this"
              + " repository can mint a token for any account. Set SPECRA_JWT_SECRET before"
              + " this instance is reachable by anyone else:  openssl rand -base64 48");
    }
  }
}
