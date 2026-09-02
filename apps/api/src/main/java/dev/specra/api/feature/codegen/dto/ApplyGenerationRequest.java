package dev.specra.api.feature.codegen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * What a person supplies when they accept a proposal.
 *
 * <p>The message is optional because the generation can write a Conventional Commit itself from the
 * case it came from; a reviewer who wants to say more replaces it.
 *
 * @param message the commit message; generated from the test case when left out
 * @param push whether to push the branch afterwards, or leave the commit local
 * @param edits corrections to the proposed bodies, by path. Absent means "commit what was
 *     proposed", which is the ordinary case.
 */
public record ApplyGenerationRequest(
    @Size(max = 500, message = "{validation.generation.message.size}") @Schema(example = "test(auth): generate login spec from TC-104")
        String message,
    @Schema(example = "false") Boolean push,
    @Size(max = 100, message = "{validation.generation.edits.size}") List<@Valid EditedFileRequest> edits) {

  public boolean pushOrDefault() {
    return Boolean.TRUE.equals(push);
  }

  public List<EditedFileRequest> editsOrEmpty() {
    return edits == null ? List.of() : edits;
  }
}
