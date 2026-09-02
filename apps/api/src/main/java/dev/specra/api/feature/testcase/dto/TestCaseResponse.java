package dev.specra.api.feature.testcase.dto;

import dev.specra.api.feature.testcase.domain.AutomationStatus;
import dev.specra.api.feature.testcase.domain.TestCasePriority;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record TestCaseResponse(
    UUID id,
    UUID projectId,
    String reference,
    String title,
    String description,
    String preconditions,
    String expectedResult,
    String externalSource,
    String externalId,
    String externalUrl,
    Instant importedAt,
    TestCasePriority priority,
    AutomationStatus automationStatus,
    boolean outOfDate,
    List<TestCaseStepResponse> steps,
    Set<String> tags,
    Instant indexedAt,
    Instant createdAt,
    Instant updatedAt) {}
