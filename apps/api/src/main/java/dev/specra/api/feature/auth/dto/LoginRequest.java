package dev.specra.api.feature.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * No {@code @Email} and no length floor on the password: this is the sign-in form, and a wrong
 * credential must come back as one {@code invalid-credentials} answer rather than as a field error
 * that tells the caller which half was wrong.
 */
public record LoginRequest(
    @NotBlank(message = "{validation.auth.email.required}") @Size(max = 320, message = "{validation.auth.email.size}") @Schema(example = "qa@specra.dev")
        String email,
    @NotBlank(message = "{validation.auth.password.required}") @Size(max = 128, message = "{validation.auth.password.size}") String password) {}
