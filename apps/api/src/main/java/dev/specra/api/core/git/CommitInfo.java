package dev.specra.api.core.git;

import java.time.Instant;

/** One line of history, as the UI renders it. */
public record CommitInfo(
    String sha, String message, String authorName, String authorEmail, Instant committedAt) {}
