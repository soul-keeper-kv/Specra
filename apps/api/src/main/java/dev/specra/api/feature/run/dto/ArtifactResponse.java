package dev.specra.api.feature.run.dto;

import dev.specra.api.feature.run.domain.ArtifactKind;
import java.time.Instant;
import java.util.UUID;

/**
 * Evidence, by reference.
 *
 * <p>No URL here: a link is minted on demand and expires, so one embedded in a cached response
 * cannot outlive its own lifetime. {@code GET /run-items/{id}/artifacts} is what issues them.
 */
public record ArtifactResponse(
    UUID id, ArtifactKind kind, String contentType, Long sizeBytes, Instant expiresAt) {}
