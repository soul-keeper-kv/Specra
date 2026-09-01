package dev.specra.api.feature.testcase.dto;

/** One step as stored: 1-based position, the author's words. */
public record TestCaseStepResponse(int position, String action, String expected) {}
