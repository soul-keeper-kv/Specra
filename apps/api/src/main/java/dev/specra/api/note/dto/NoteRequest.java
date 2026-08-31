package dev.specra.api.note.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record NoteRequest(
    @NotBlank @Size(max = 200) @Schema(example = "Kickoff notes") String title,
    @NotBlank @Schema(example = "We agreed to ship the pgvector spike first.") String content,
    @Schema(example = "[\"meeting\",\"q3\"]") Set<@Size(max = 64) String> tags) {}
