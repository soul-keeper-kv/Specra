package dev.specra.api.feature.auth.service;

import dev.specra.api.config.SpecraProperties;
import dev.specra.api.core.error.ErrorCode;
import dev.specra.api.core.error.UnauthorizedException;
import dev.specra.api.core.security.AuthenticatedUser;
import dev.specra.api.feature.auth.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

/**
 * Mints and verifies the access token, and nothing else.
 *
 * <p>The token is a JWT signed with the installation's own secret, so verifying one is a signature
 * check and no database round trip — which is what keeps an authenticated request to the queries it
 * actually needs. The price is that the token stays valid until it expires: revocation lives on the
 * refresh token, and {@code specra.security.jwt.access-minutes} is how long a revoked session can
 * still read.
 *
 * <p>Identity claims only. A workspace role is per workspace and changes without the token
 * changing, so putting one in here would mean either a stale grant or a re-issue on every role
 * edit; {@code WorkspaceAccess} reads the current one from the row instead.
 */
@Service
public class TokenService {

  private static final Logger log = LoggerFactory.getLogger(TokenService.class);

  /** Non-standard, so they are namespaced to avoid colliding with a registered claim later. */
  private static final String CLAIM_EMAIL = "email";

  private static final String CLAIM_NAME = "name";

  private final JwtEncoder encoder;
  private final JwtDecoder decoder;
  private final SpecraProperties.Security.Jwt config;
  private final Clock clock;

  public TokenService(
      JwtEncoder encoder, JwtDecoder decoder, SpecraProperties properties, Clock clock) {
    this.encoder = encoder;
    this.decoder = decoder;
    this.config = properties.security().jwt();
    this.clock = clock;
  }

  /** An access token and the moment it stops being accepted. */
  public record AccessToken(String value, Instant expiresAt) {}

  public AccessToken issue(User user) {
    Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
    Instant expiresAt = now.plusSeconds(config.accessMinutes() * 60L);

    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(config.issuer())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .subject(user.getId().toString())
            .id(UUID.randomUUID().toString())
            .claim(CLAIM_EMAIL, user.getEmail())
            .claim(CLAIM_NAME, user.getDisplayName())
            .build();

    String value =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    return new AccessToken(value, expiresAt);
  }

  /**
   * Turns a bearer token into the caller, or refuses.
   *
   * <p>Every failure is the same failure to the client — expired, forged, truncated, signed with
   * yesterday's secret. The distinction matters to whoever is reading the log and to nobody else,
   * so it is logged at debug and answered with one code.
   */
  public AuthenticatedUser verify(String token) {
    Jwt jwt;
    try {
      jwt = decoder.decode(token);
    } catch (JwtException e) {
      log.debug("Rejected access token: {}", e.getMessage());
      throw invalidToken();
    }

    try {
      return new AuthenticatedUser(
          UUID.fromString(jwt.getSubject()),
          jwt.getClaimAsString(CLAIM_EMAIL),
          jwt.getClaimAsString(CLAIM_NAME));
    } catch (IllegalArgumentException | NullPointerException e) {
      // A token we signed, carrying claims we do not recognise: an older format, or a secret
      // shared with something that is not this application. Neither is a caller error to explain.
      log.warn("Access token verified but its claims are unusable", e);
      throw invalidToken();
    }
  }

  private static UnauthorizedException invalidToken() {
    return new UnauthorizedException(ErrorCode.INVALID_TOKEN, ErrorCode.INVALID_TOKEN.detailKey());
  }
}
