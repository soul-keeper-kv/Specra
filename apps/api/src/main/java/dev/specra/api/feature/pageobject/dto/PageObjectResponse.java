package dev.specra.api.feature.pageobject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param inspectedAt null when the page exists only because an IR referenced it — the UI shows
 *     "never inspected" from this, which is the state that makes a generation unresolvable
 */
public record PageObjectResponse(
    UUID id,
    UUID projectId,
    @Schema(example = "LoginPage") String name,
    String route,
    Instant inspectedAt,
    List<PageElementResponse> elements,
    Instant updatedAt) {}
