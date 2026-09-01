package dev.specra.api.core.git;

/** The commit that was just created. */
public record CommitResult(String sha, String message) {}
