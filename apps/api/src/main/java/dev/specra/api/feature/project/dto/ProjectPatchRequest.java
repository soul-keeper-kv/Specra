package dev.specra.api.feature.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A partial update: a field left out is a field left alone.
 *
 * <p>Neither {@code key} nor {@code engine} appears here on purpose. The key is quoted in test case
 * references and in commit messages, and the engine decides what the generated code already is —
 * changing either silently invalidates work that exists.
 */
public record ProjectPatchRequest(
    @Size(max = 120, message = "{validation.project.name.size}") @Pattern(regexp = ".*\\S.*", message = "{validation.project.name.required}") @Schema(example = "Acme storefront")
        String name,
    @Size(max = 2000, message = "{validation.project.description.size}") @Schema(example = "Checkout and account journeys for the public storefront.")
        String description) {}
