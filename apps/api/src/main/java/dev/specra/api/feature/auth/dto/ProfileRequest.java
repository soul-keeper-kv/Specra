package dev.specra.api.feature.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

/**
 * What a user may change about themselves. The email is not here: changing it is a different
 * operation with a verification step behind it, and letting it through a profile patch would be a
 * silent account takeover primitive.
 */
public record ProfileRequest(
    @NotBlank(message = "{validation.auth.displayName.required}") @Size(max = 120, message = "{validation.auth.displayName.size}") @Schema(example = "Nguyen Thi QA")
        String displayName,
    @Nullable @Size(max = 10, message = "{validation.auth.locale.size}") @Schema(
            description = "Preferred language tag, or null to follow the browser",
            example = "vi")
        String locale) {}
