package dev.specra.api.feature.run.dto;

/** The counts a run list shows without opening anything. */
public record RunTotals(int total, int passed, int failed, int errored, int skipped) {}
