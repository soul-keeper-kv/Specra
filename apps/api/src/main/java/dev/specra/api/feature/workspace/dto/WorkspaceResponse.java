package dev.specra.api.feature.workspace.dto;

import dev.specra.api.feature.workspace.domain.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * @param role what the caller is here. Every workspace a request can see is one the caller belongs
 *     to, so this is never null on a listing — and it is what lets the UI decide, from the same
 *     response that draws the workspace, which of its actions to offer.
 */
public record WorkspaceResponse(
    UUID id,
    String name,
    String slug,
    @Schema(description = "The calling user's role in this workspace") WorkspaceRole role,
    Instant createdAt,
    Instant updatedAt) {}
