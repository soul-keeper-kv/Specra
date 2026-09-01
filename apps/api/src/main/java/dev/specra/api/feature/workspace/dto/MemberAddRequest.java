package dev.specra.api.feature.workspace.dto;

import dev.specra.api.feature.workspace.domain.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Adds someone who already has an account.
 *
 * <p>By address rather than by id, because the person doing this knows an email and has no way to
 * know a uuid. An address with no account behind it is a 404 and not a silent invitation: sending
 * mail is a capability this installation does not have yet, and pretending otherwise would leave
 * the inviter waiting for someone who was never told.
 */
public record MemberAddRequest(
    @NotBlank(message = "{validation.auth.email.required}") @Email(message = "{validation.auth.email.invalid}") @Size(max = 320, message = "{validation.auth.email.size}") @Schema(example = "qa@specra.dev")
        String email,
    @NotNull(message = "{validation.member.role.required}") @Schema(example = "MEMBER")
        WorkspaceRole role) {}
