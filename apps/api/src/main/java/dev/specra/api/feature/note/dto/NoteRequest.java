package dev.specra.api.feature.note.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record NoteRequest(
    @NotBlank(message = "{validation.note.title.required}") @Size(max = 200, message = "{validation.note.title.size}") @Schema(example = "Kickoff notes")
        String title,
    @NotBlank(message = "{validation.note.content.required}") @Schema(example = "We agreed to ship the pgvector spike first.")
        String content,
    @Schema(example = "[\"meeting\",\"q3\"]")
        Set<@Size(max = 64, message = "{validation.note.tag.size}") String> tags) {}
