package dev.specra.api.feature.project.dto;

import dev.specra.api.feature.project.domain.AutomationEngine;
import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
    UUID id,
    UUID workspaceId,
    String key,
    String name,
    String description,
    AutomationEngine engine,
    Instant createdAt,
    Instant updatedAt) {}
