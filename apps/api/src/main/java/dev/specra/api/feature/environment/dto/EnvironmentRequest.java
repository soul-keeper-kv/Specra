package dev.specra.api.feature.environment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * @param baseUrl where the suite points; the one thing that makes a spec portable between
 *     deployments, so it is required rather than defaulted
 * @param isDefault at most one per project — setting it here clears whichever held it
 * @param variables replaces the whole list, in order. A secret whose value is omitted keeps the
 *     stored one, which is what lets the form be re-saved without retyping every credential.
 * @param preludeTestCaseId a test case replayed before an inspection of this environment, so a page
 *     behind a sign-in — or behind any other state a person had to set up by hand — can be read.
 *     Null clears it.
 */
public record EnvironmentRequest(
    @NotBlank(message = "{validation.environment.name.required}") @Size(max = 64, message = "{validation.environment.name.size}") @Schema(example = "STAGING")
        String name,
    @NotBlank(message = "{validation.environment.base-url.required}") @Size(max = 500, message = "{validation.environment.base-url.size}") @Pattern(regexp = "^https?://.+", message = "{validation.environment.base-url.format}") @Schema(example = "https://staging.acme.dev")
        String baseUrl,
    @Schema(example = "false") Boolean isDefault,
    @Size(max = 100, message = "{validation.environment.variables.size}") List<@Valid EnvironmentVariableRequest> variables,
    UUID preludeTestCaseId) {}
