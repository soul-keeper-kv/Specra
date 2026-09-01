package dev.specra.api.feature.git.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CheckoutRequest(
    @NotBlank(message = "{validation.git.branch.required}") @Size(max = 200, message = "{validation.git.branch.size}") @Schema(example = "main")
        String branch) {}
