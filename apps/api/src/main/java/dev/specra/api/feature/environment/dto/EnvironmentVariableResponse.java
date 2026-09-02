package dev.specra.api.feature.environment.dto;

/**
 * A variable as a reader may see it.
 *
 * <p>{@code value} is the real value for a plain variable and <b>null</b> for a secret — product
 * invariant 6, expressed in the type rather than in a comment. {@code valueSet} is how the UI shows
 * "configured" without the API ever having to decide whether this particular caller is trusted
 * enough to be told a password.
 */
public record EnvironmentVariableResponse(
    String key, String value, boolean secret, boolean valueSet) {}
