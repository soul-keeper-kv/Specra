package dev.specra.api.feature.testmanagement.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TestManagementBindingResponse(
    UUID id,
    UUID connectionId,
    String connectionName,
    String provider,
    Map<String, String> configuration,
    String remoteProjectId,
    Instant updatedAt) {}
