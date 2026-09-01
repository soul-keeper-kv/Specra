package dev.specra.api.feature.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param slug optional on the way in — left out, it is derived from the name.
 */
public record WorkspaceRequest(
    @NotBlank(message = "{validation.workspace.name.required}") @Size(max = 120, message = "{validation.workspace.name.size}") @Schema(example = "Acme QA")
        String name,
    @Size(max = 64, message = "{validation.workspace.slug.size}") @Pattern(
            regexp = "^$|^[a-z0-9]+(-[a-z0-9]+)*$",
            message = "{validation.workspace.slug.format}")
        @Schema(example = "acme-qa")
        String slug) {}
