package dev.specra.api.feature.testmodel.dto;

import java.util.List;

/**
 * A page the model refers to, and through which steps. Until inspection has run (M8) every one of
 * these is a proposal waiting for a DOM — which is exactly what the workspace shows.
 */
public record PageReference(String name, List<String> elements, List<String> stepIds) {}
