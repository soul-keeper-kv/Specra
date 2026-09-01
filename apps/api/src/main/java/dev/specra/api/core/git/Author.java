package dev.specra.api.core.git;

/**
 * Who a commit is attributed to — the person who approved the change, never the tool that wrote the
 * bytes. The tool appears as the committer, which the provider sets from configuration.
 */
public record Author(String name, String email) {}
