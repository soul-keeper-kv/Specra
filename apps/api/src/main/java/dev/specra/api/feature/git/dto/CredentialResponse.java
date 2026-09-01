package dev.specra.api.feature.git.dto;

import java.time.Instant;
import java.util.UUID;

/** Never the token — {@code tokenSet} is all a reader may learn about it. */
public record CredentialResponse(
    UUID id, String name, String username, boolean tokenSet, Instant updatedAt) {}
