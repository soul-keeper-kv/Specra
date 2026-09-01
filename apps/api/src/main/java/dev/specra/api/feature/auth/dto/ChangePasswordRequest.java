package dev.specra.api.feature.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank(message = "{validation.auth.currentPassword.required}") @Size(max = 128, message = "{validation.auth.password.size}") String currentPassword,
    @NotBlank(message = "{validation.auth.newPassword.required}") @Size(min = 10, max = 128, message = "{validation.auth.password.size}") String newPassword) {}
