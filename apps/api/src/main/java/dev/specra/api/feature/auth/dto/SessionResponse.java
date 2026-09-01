package dev.specra.api.feature.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One live refresh token, as the "signed-in devices" screen shows it.
 *
 * <p>{@code current} is what makes the list usable: without it, revoking a session is a guess, and
 * the most likely guess is the one the user is sitting in front of.
 */
public record SessionResponse(
    UUID id,
    @Schema(description = "Raw User-Agent of the client that signed in", example = "Mozilla/5.0 …")
        String userAgent,
    @Schema(example = "203.0.113.7") String clientIp,
    Instant createdAt,
    Instant lastUsedAt,
    Instant expiresAt,
    @Schema(description = "True for the session making this request") boolean current) {}
