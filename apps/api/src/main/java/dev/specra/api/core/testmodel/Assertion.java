package dev.specra.api.core.testmodel;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The condition an {@code assert} states, or a {@code waitFor} waits for.
 *
 * <p>{@code expected} is {@code Object} for the same reason as {@link TestValue#value()}: the
 * schema allows a string, a number or a boolean, and which one is legal depends on the condition.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Assertion(AssertCondition condition, Object expected, String attribute) {}
