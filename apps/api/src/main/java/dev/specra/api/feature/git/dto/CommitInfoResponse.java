package dev.specra.api.feature.git.dto;

import java.time.Instant;

/** One row of history. */
public record CommitInfoResponse(
    String sha, String message, String authorName, String authorEmail, Instant committedAt) {}
