package dev.specra.api.feature.testmodel.dto;

import dev.specra.api.core.testmodel.TestModel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One stored IR version with what a reviewer needs beside it: the pages it names and how the manual
 * steps map onto it.
 */
public record TestModelResponse(
    UUID id,
    UUID testCaseId,
    int version,
    int irVersion,
    TestModel document,
    String checksum,
    Instant createdAt,
    List<PageReference> pages,
    List<SourceCoverage> coverage) {}
