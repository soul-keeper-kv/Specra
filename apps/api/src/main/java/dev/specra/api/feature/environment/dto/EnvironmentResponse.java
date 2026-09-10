package dev.specra.api.feature.environment.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** No secret value appears here, or anywhere else a client can reach. */
public record EnvironmentResponse(
    UUID id,
    UUID projectId,
    String name,
    String baseUrl,
    boolean isDefault,
    List<EnvironmentVariableResponse> variables,
    /** The test case replayed before an inspection here; null when nothing has to happen first. */
    UUID preludeTestCaseId,
    Instant createdAt,
    Instant updatedAt) {}
