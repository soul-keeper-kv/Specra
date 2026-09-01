package dev.specra.api.feature.git.dto;

/** One text file out of the working copy. */
public record FileContentResponse(String path, String content) {}
