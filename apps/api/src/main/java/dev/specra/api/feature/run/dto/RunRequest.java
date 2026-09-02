package dev.specra.api.feature.run.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * What to run.
 *
 * @param testCaseIds which cases; empty or null runs every automated case in the project
 * @param browsers the matrix; empty falls back to chromium, the one every install has
 * @param environmentId where to point; null uses the project's default environment
 */
public record RunRequest(
    @Size(max = 500, message = "{validation.run.test-cases.size}") List<UUID> testCaseIds,
    @Size(max = 3, message = "{validation.run.browsers.size}") @Schema(example = "[\"chromium\"]")
        List<String> browsers,
    UUID environmentId) {}
