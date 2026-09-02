package dev.specra.api.feature.pageobject.dto;

import dev.specra.api.feature.pageobject.domain.LocatorStrategy;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * @param qualifier the accessible name a `ROLE` locator needs; null for every other strategy
 * @param confidence 0–1 as the planner scored it, shown so a person can distrust a weak locator
 *     rather than discovering it is weak when a test flakes
 */
public record PageElementResponse(
    UUID id,
    @Schema(example = "submitButton") String name,
    LocatorStrategy strategy,
    String value,
    String qualifier,
    LocatorStrategy fallbackStrategy,
    String fallbackValue,
    BigDecimal confidence) {}
