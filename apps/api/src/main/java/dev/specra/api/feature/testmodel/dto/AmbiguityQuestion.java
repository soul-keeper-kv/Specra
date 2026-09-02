package dev.specra.api.feature.testmodel.dto;

/**
 * One thing understanding could not resolve without guessing.
 *
 * @param sourceStepId the manual step it is about ({@code ts-3}), or null for the case as a whole
 * @param question what a tester would have to add to the case, in the case's own language
 */
public record AmbiguityQuestion(String sourceStepId, String question) {}
