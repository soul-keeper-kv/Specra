package dev.specra.api.feature.git.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param from the branch the new one starts at; null means the repository's default branch
 */
public record BranchRequest(
    @NotBlank(message = "{validation.git.branch.required}") @Size(max = 200, message = "{validation.git.branch.size}") @Schema(example = "specra/tc-104-login")
        String name,
    @Size(max = 200, message = "{validation.git.branch.size}") @Schema(example = "main")
        String from) {}
