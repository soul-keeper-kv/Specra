package dev.specra.api.feature.git.dto;

/** The commit that was created; the sha is what the UI links and quotes. */
public record CommitResponse(String sha, String message) {}
