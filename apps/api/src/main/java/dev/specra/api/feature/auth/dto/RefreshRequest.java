package dev.specra.api.feature.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshRequest(
    @NotBlank(message = "{validation.auth.refreshToken.required}") @Size(max = 200, message = "{validation.auth.refreshToken.size}") @Schema(
            description =
                "The opaque value returned as refreshToken by login, register or a previous"
                    + " refresh")
        String refreshToken) {}
