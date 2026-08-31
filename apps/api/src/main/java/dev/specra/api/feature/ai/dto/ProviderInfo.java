package dev.specra.api.feature.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
    description =
        "Which model providers are wired in right now. Everything here is driven by "
            + "spring.ai.model.* properties, so it changes without recompiling.")
public record ProviderInfo(
    @Schema(example = "anthropic") String chatProvider,
    @Schema(example = "transformers") String embeddingProvider,
    @Schema(example = "AnthropicChatModel") String chatModelType,
    @Schema(example = "TransformersEmbeddingModel") String embeddingModelType,
    @Schema(description = "Vector width the pgvector table must match", example = "384")
        int embeddingDimensions,
    @Schema(description = "Providers whose starter is on the classpath")
        List<String> availableProviders) {}
