package dev.specra.api.feature.pageobject.dto;

import dev.specra.api.feature.pageobject.domain.LocatorStrategy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A locator a person corrected.
 *
 * @param qualifier the accessible name a `ROLE` locator needs. Ignored for every other strategy,
 *     rather than rejected: a client that leaves it set while switching strategy has made a
 *     harmless mistake, and refusing the edit over it would be pedantry.
 */
public record ElementUpdateRequest(
    @NotNull(message = "{validation.page-element.strategy.required}") LocatorStrategy strategy,
    @NotBlank(message = "{validation.page-element.value.required}") @Size(max = 1000, message = "{validation.page-element.value.size}") @Schema(example = "submit-button")
        String value,
    @Size(max = 500, message = "{validation.page-element.qualifier.size}") String qualifier) {}
