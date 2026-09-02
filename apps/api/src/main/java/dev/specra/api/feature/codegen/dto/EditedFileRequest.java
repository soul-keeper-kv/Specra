package dev.specra.api.feature.codegen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One file a reviewer corrected before accepting the proposal.
 *
 * <p>Edits travel with the apply request rather than mutating the stored proposal, which is
 * deliberately immutable ({@code files} is {@code updatable = false}). That keeps the row honest as
 * an audit record of what the model actually produced, while the commit carries what the human
 * approved — the two are genuinely different facts, and the {@code ai_generations} trail is worth
 * nothing if a human edit can rewrite it afterwards.
 *
 * <p>A path that the proposal does not contain is rejected rather than written: accepting one would
 * turn apply into a general "commit any file" endpoint, which is not what a reviewer agreed to and
 * not what the generation was reviewed as.
 *
 * @param path the proposed file's path, exactly as the proposal gives it
 * @param contents the body to commit instead of the proposed one
 */
public record EditedFileRequest(
    @NotBlank(message = "{validation.generation.edit.path.required}") @Size(max = 500, message = "{validation.generation.edit.path.size}") @Schema(example = "tests/specs/login.spec.ts")
        String path,
    // Not @NotBlank: emptying a generated file is a legitimate correction, and the reviewer is
    // the authority on that. Null is not, because it means the client forgot the field.
    @NotNull(message = "{validation.generation.edit.contents.required}") @Size(max = 1000000, message = "{validation.generation.edit.contents.size}") String contents) {}
