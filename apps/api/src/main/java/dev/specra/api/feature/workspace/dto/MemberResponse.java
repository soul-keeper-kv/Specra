package dev.specra.api.feature.workspace.dto;

import dev.specra.api.feature.workspace.domain.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One person in one workspace.
 *
 * <p>The user's id and address are flattened in rather than nested: the members screen shows a list
 * of people, and a client that has to unwrap a user object to render a row is a client that has to
 * know what a user is.
 */
public record MemberResponse(
    @Schema(description = "Id of the membership, not of the user") UUID id,
    UUID userId,
    @Schema(example = "qa@specra.dev") String email,
    @Schema(example = "Nguyen Thi QA") String displayName,
    WorkspaceRole role,
    Instant createdAt) {}
