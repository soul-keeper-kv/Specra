package dev.specra.api.feature.testmanagement.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TestManagementConnectionResponse(
    UUID id,
    String name,
    String provider,
    Map<String, String> configuration,
    boolean credentialsSet,
    Instant updatedAt) {}
