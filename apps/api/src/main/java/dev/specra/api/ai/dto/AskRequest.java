package dev.specra.api.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskRequest(
    @NotBlank @Size(max = 4000) @Schema(example = "What did we decide about the database?")
        String question,
    @Min(1) @Max(20) @Schema(defaultValue = "4") Integer topK,
    @Schema(defaultValue = "0.5", description = "Minimum cosine similarity, 0..1")
        Double similarityThreshold) {

  public int topKOrDefault() {
    return topK == null ? 4 : topK;
  }

  public double thresholdOrDefault() {
    return similarityThreshold == null ? 0.5 : similarityThreshold;
  }
}
