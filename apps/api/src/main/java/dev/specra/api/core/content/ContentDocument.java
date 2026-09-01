package dev.specra.api.core.content;

import java.time.Instant;
import java.util.Set;

/**
 * One stored document, in full, as every store exposes it.
 *
 * <p>Deliberately not a JPA entity and deliberately not a test case: this is the shape the AI layer
 * and any future caller sees, so swapping the store underneath changes nothing above it.
 *
 * @param kind which {@link ContentStore} owns it — the value of {@link ContentStore#kind()}
 * @param id opaque to the caller; only the owning store knows how to parse it
 */
public record ContentDocument(
    String kind,
    String id,
    String title,
    String body,
    Set<String> tags,
    Instant createdAt,
    Instant updatedAt) {}
