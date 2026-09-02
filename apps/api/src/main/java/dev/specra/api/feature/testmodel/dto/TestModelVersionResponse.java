package dev.specra.api.feature.testmodel.dto;

import java.time.Instant;
import java.util.UUID;

/** A row of the version history; the document itself is fetched by version when wanted. */
public record TestModelVersionResponse(
    UUID id, int version, int irVersion, String checksum, Instant createdAt, int stepCount) {}
