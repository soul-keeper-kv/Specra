package dev.specra.api.feature.git.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param username what the host pairs with the token; optional — GitHub PATs accept anything
 * @param token write-only, encrypted at rest, never returned
 */
public record CredentialRequest(
    @NotBlank(message = "{validation.git.credential.name.required}") @Size(max = 120, message = "{validation.git.credential.name.size}") @Schema(example = "acme-bot PAT")
        String name,
    @Size(max = 120, message = "{validation.git.credential.username.size}") @Schema(example = "acme-bot")
        String username,
    @NotBlank(message = "{validation.git.credential.token.required}") @Size(max = 500, message = "{validation.git.credential.token.size}") @Schema(description = "Write-only; stored encrypted, never returned")
        String token) {}
