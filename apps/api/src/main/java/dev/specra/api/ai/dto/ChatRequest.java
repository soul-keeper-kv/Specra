package dev.specra.api.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
    @NotBlank @Size(max = 8000) @Schema(example = "Summarise what I wrote about pgvector.")
        String message,
    @Schema(
            description = "Conversation id. Reuse it to keep history; omit for a fresh thread.",
            example = "default")
        String conversationId) {

  public String conversationIdOrDefault() {
    return conversationId == null || conversationId.isBlank() ? "default" : conversationId;
  }
}
