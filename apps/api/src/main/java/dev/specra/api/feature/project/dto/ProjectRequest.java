package dev.specra.api.feature.project.dto;

import dev.specra.api.feature.project.domain.AutomationEngine;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param key optional on the way in — left out, it is derived from the name. It is what appears in
 *     every reference a human quotes, so it cannot be changed afterwards.
 * @param engine optional; there is one engine today and the default is it.
 */
public record ProjectRequest(
    @NotBlank(message = "{validation.project.name.required}") @Size(max = 120, message = "{validation.project.name.size}") @Schema(example = "Acme storefront")
        String name,
    @Size(max = 16, message = "{validation.project.key.size}") @Pattern(regexp = "^$|^[A-Z][A-Z0-9]*$", message = "{validation.project.key.format}") @Schema(example = "ACME")
        String key,
    @Size(max = 2000, message = "{validation.project.description.size}") @Schema(example = "Checkout and account journeys for the public storefront.")
        String description,
    @Schema(example = "PLAYWRIGHT") AutomationEngine engine) {}
