package dev.specra.api.feature.pageobject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * What to look at, and what to call it.
 *
 * @param pageName the IR's name for this page. Supplied rather than derived, because inspection has
 *     no idea that `/login` is `LoginPage` and guessing would produce a page nothing references.
 * @param route joined to the environment's base URL. A path rather than a URL so the same page
 *     object can be re-inspected against staging or production without editing it.
 * @param environmentId which deployment to look at; null uses the project's default
 */
public record InspectRequest(
    @NotBlank(message = "{validation.page-object.name.required}") @Size(max = 120, message = "{validation.page-object.name.size}") @Pattern(
            regexp = "^[A-Za-z][A-Za-z0-9]*$",
            message = "{validation.page-object.name.format}")
        @Schema(example = "LoginPage")
        String pageName,
    @NotBlank(message = "{validation.page-object.route.required}") @Size(max = 500, message = "{validation.page-object.route.size}") @Schema(example = "/login")
        String route,
    UUID environmentId) {}
