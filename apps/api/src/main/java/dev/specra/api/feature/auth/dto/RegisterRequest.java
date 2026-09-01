package dev.specra.api.feature.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "{validation.auth.email.required}") @Email(message = "{validation.auth.email.invalid}") @Size(max = 320, message = "{validation.auth.email.size}") @Schema(example = "qa@specra.dev")
        String email,
    @NotBlank(message = "{validation.auth.displayName.required}") @Size(max = 120, message = "{validation.auth.displayName.size}") @Schema(example = "Nguyen Thi QA")
        String displayName,
    /*
     * The floor is a length and nothing else. Composition rules ("one digit, one symbol") make
     * passwords shorter and more predictable, and NIST 800-63B has recommended against them since
     * 2017; the ceiling exists only so a megabyte does not reach bcrypt.
     */
    @NotBlank(message = "{validation.auth.password.required}") @Size(min = 10, max = 128, message = "{validation.auth.password.size}") @Schema(example = "correct horse battery staple", minLength = 10)
        String password) {}
