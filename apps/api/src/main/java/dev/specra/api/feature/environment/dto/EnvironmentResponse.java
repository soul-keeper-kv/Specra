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
    Instant createdAt,
    Instant updatedAt) {}
