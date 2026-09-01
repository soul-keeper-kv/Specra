package dev.specra.api.feature.git.dto;

import dev.specra.api.feature.git.domain.GitProviderKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Connect (or re-point) the project's one repository.
 *
 * @param provider which host; only hosts with an installed implementation are accepted
 * @param credentialId a stored credential in the same workspace; null for public or local remotes
 */
public record RepositoryRequest(
    @NotNull(message = "{validation.git.provider.required}") @Schema(example = "GITHUB")
        GitProviderKind provider,
    @NotBlank(message = "{validation.git.remote-url.required}") @Size(max = 500, message = "{validation.git.remote-url.size}") @Schema(example = "https://github.com/acme/storefront-e2e.git")
        String remoteUrl,
    @NotBlank(message = "{validation.git.default-branch.required}") @Size(max = 200, message = "{validation.git.default-branch.size}") @Schema(example = "main")
        String defaultBranch,
    UUID credentialId) {}
