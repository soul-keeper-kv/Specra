package dev.specra.api.feature.workspace.dto;

import dev.specra.api.feature.workspace.domain.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record MemberRoleRequest(
    @NotNull(message = "{validation.member.role.required}") @Schema(example = "ADMIN")
        WorkspaceRole role) {}
