package dev.specra.api.feature.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * What register, login, refresh and change-password all return.
 *
 * <p>Two tokens with different jobs. The access token is a short-lived signed JWT sent as {@code
 * Authorization: Bearer …} on every call and never looked up in the database, which is what keeps
 * an authenticated request to one query. The refresh token is an opaque random string that is only
 * ever presented to {@code /api/auth/refresh}, is stored hashed, and is rotated on every use.
 *
 * <p>{@code expiresAt} is absolute rather than a "seconds from now" count, so a client that queued
 * the response for a moment does not schedule its refresh from a clock that has already moved.
 */
public record AuthTokens(
    @Schema(example = "Bearer") String tokenType,
    @Schema(description = "Signed JWT; send it as the Authorization header") String accessToken,
    @Schema(description = "When accessToken stops being accepted") Instant expiresAt,
    @Schema(description = "Opaque; exchange it at /api/auth/refresh, which invalidates it")
        String refreshToken,
    @Schema(description = "When refreshToken stops being accepted") Instant refreshExpiresAt,
    AccountResponse user) {

  public static final String BEARER = "Bearer";

  public static AuthTokens bearer(
      String accessToken,
      Instant expiresAt,
      String refreshToken,
      Instant refreshExpiresAt,
      AccountResponse user) {
    return new AuthTokens(BEARER, accessToken, expiresAt, refreshToken, refreshExpiresAt, user);
  }
}
