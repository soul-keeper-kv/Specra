package dev.specra.api.feature.git.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * One commit, of exactly the paths the user picked — never "everything that happens to be dirty",
 * because a commit that bundles unrelated changes is not reviewable, and reviewability is the point
 * (07-git.md).
 */
public record CommitRequest(
    @NotBlank(message = "{validation.git.message.required}") @Size(max = 500, message = "{validation.git.message.size}") @Schema(example = "test(auth): tighten the login expectations")
        String message,
    @NotEmpty(message = "{validation.git.paths.required}") List<
                @NotBlank(message = "{validation.git.path.required}") @Size(max = 500, message = "{validation.git.path.size}") String>
            paths) {}
