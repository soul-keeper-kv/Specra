package dev.specra.api.feature.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Named ChatReply rather than ChatResponse to stay clear of Spring AI's own type. */
public record ChatReply(
    String content,
    @Schema(example = "default") String conversationId,
    @Schema(description = "Which provider actually answered", example = "anthropic")
        String provider,
    @Schema(example = "claude-sonnet-4-5") String model) {}
