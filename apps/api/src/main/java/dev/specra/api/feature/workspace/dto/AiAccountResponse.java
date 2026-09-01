package dev.specra.api.feature.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Never carries the key itself — {@code keySet} is all a reader may learn about it. */
public record AiAccountResponse(
    UUID workspaceId,
    String provider,
    String chatModel,
    String embeddingModel,
    BigDecimal monthlyBudgetUsd,
    @Schema(description = "Whether an API key is stored for this workspace") boolean keySet,
    Instant updatedAt) {}
