package dev.specra.api.feature.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * @param provider lower-case identifier from the {@code spring.ai.model.chat} vocabulary — a value,
 *     never a vendor class
 * @param apiKey write-only: {@code null} keeps the stored key, an empty string clears it, any other
 *     value replaces it. Reads never return it — only whether one is set.
 */
public record AiAccountRequest(
    @NotBlank(message = "{validation.aiaccount.provider.required}") @Size(max = 32, message = "{validation.aiaccount.provider.size}") @Pattern(regexp = "^[a-z][a-z0-9-]*$", message = "{validation.aiaccount.provider.format}") @Schema(example = "anthropic")
        String provider,
    @Size(max = 500, message = "{validation.aiaccount.api-key.size}") @Schema(description = "Write-only; null keeps, empty clears, a value replaces")
        String apiKey,
    @Size(max = 120, message = "{validation.aiaccount.model.size}") @Schema(example = "claude-sonnet-4-5")
        String chatModel,
    @Size(max = 120, message = "{validation.aiaccount.model.size}") @Schema(example = "text-embedding-3-small")
        String embeddingModel,
    @DecimalMin(value = "0", message = "{validation.aiaccount.budget.min}") @Digits(integer = 10, fraction = 2, message = "{validation.aiaccount.budget.digits}") @Schema(example = "50.00")
        BigDecimal monthlyBudgetUsd) {}
