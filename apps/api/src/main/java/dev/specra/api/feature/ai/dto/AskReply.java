package dev.specra.api.feature.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AskReply(
    String answer,
    @Schema(description = "Chunks retrieved from pgvector that grounded the answer")
        List<Source> sources,
    String provider,
    String model) {

  public record Source(
      @Schema(example = "Kickoff notes") String title,
      @Schema(description = "Id of the note this chunk came from") String noteId,
      @Schema(description = "The retrieved chunk, truncated for display") String excerpt,
      @Schema(example = "0.83") Double score) {}
}
