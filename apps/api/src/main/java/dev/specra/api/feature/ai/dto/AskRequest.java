package dev.specra.api.feature.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskRequest(
    @NotBlank(message = "{validation.ask.question.required}") @Size(max = 4000, message = "{validation.ask.question.size}") @Schema(example = "What did we decide about the database?")
        String question,
    @Min(value = 1, message = "{validation.ask.top-k.range}") @Max(value = 20, message = "{validation.ask.top-k.range}") @Schema(defaultValue = "4")
        Integer topK,
    @DecimalMin(value = "0.0", message = "{validation.ask.threshold.range}") @DecimalMax(value = "1.0", message = "{validation.ask.threshold.range}") @Schema(defaultValue = "0.5", description = "Minimum cosine similarity, 0..1")
        Double similarityThreshold) {

  public int topKOrDefault() {
    return topK == null ? 4 : topK;
  }

  public double thresholdOrDefault() {
    return similarityThreshold == null ? 0.5 : similarityThreshold;
  }
}
