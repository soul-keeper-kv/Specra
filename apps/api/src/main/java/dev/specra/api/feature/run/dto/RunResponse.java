package dev.specra.api.feature.run.dto;

import dev.specra.api.feature.run.domain.RunStatus;
import dev.specra.api.feature.run.domain.RunTrigger;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param commitSha the tree this run describes; two people opening it see the same code
 * @param dirty whether uncommitted changes were part of it — the diff itself is on the detail
 */
public record RunResponse(
    UUID id,
    UUID projectId,
    String reference,
    UUID environmentId,
    String commitSha,
    boolean dirty,
    RunTrigger trigger,
    RunStatus status,
    Instant queuedAt,
    Instant startedAt,
    Instant completedAt,
    String errorMessage,
    RunTotals totals,
    List<RunItemResponse> items) {}
