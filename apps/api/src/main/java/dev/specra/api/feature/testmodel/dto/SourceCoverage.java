package dev.specra.api.feature.testmodel.dto;

import java.util.List;

/**
 * Which model steps a manual step became. An empty {@code modelStepIds} is a manual step the model
 * silently dropped — a gap the reviewer must see, not a detail to hide.
 */
public record SourceCoverage(String sourceStepId, int position, List<String> modelStepIds) {}
