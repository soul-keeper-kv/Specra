package dev.specra.api.feature.run.dto;

import dev.specra.api.feature.run.domain.ArtifactKind;
import java.time.Instant;
import java.util.UUID;

/**
 * One piece of evidence and a link the browser can fetch directly.
 *
 * <p>{@code url} is signed and short-lived, and {@code expiresAt} says how long it has — so a UI
 * can re-request rather than showing a link that silently stops working. The bytes never pass
 * through this API (06-execution.md).
 */
public record ArtifactLinkResponse(
    UUID id,
    ArtifactKind kind,
    String url,
    String contentType,
    Long sizeBytes,
    Instant expiresAt) {}
