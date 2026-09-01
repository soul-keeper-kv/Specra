package dev.specra.api.feature.git.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Write one text file into the working copy — a person's edit, staged for nothing until they commit
 * it. The 2 MB ceiling is about editing text in a browser, not about git.
 */
public record FileWriteRequest(
    @NotBlank(message = "{validation.git.path.required}") @Size(max = 500, message = "{validation.git.path.size}") @Schema(example = "tests/auth/login.spec.ts")
        String path,
    @NotNull @Size(max = 2_000_000, message = "{validation.git.content.size}") String content) {}
