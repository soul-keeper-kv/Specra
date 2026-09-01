package dev.specra.api.feature.auth.dto;

import dev.specra.api.feature.auth.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** The signed-in user, as the web app's session store holds it. Never carries a hash. */
public record AccountResponse(
    UUID id,
    @Schema(example = "qa@specra.dev") String email,
    @Schema(example = "Nguyen Thi QA") String displayName,
    UserStatus status,
    @Schema(description = "Preferred language tag, null when the browser decides", example = "vi")
        String locale,
    Instant lastLoginAt,
    Instant createdAt) {}
