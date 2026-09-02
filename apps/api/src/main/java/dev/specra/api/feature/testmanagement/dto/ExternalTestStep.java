package dev.specra.api.feature.testmanagement.dto;

/**
 * One provider-neutral manual step. Data stays separate so an import never loses Xray semantics.
 */
public record ExternalTestStep(int position, String action, String data, String expected) {}
