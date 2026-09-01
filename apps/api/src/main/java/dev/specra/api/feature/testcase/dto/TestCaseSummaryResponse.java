package dev.specra.api.feature.testcase.dto;

import dev.specra.api.feature.testcase.domain.AutomationStatus;
import dev.specra.api.feature.testcase.domain.TestCasePriority;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A list row: enough for the table and the picker, without dragging every step of every case across
 * the wire to render twenty lines.
 */
public record TestCaseSummaryResponse(
    UUID id,
    UUID projectId,
    String reference,
    String title,
    TestCasePriority priority,
    AutomationStatus automationStatus,
    boolean outOfDate,
    Set<String> tags,
    Instant updatedAt) {}
