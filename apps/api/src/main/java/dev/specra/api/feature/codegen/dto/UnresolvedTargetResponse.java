package dev.specra.api.feature.codegen.dto;

/**
 * A step whose target has no locator yet — the page was never inspected, or it was and this element
 * is not on it.
 *
 * <p>Reported rather than guessed: a model inventing {@code #login-btn} is the largest source of
 * flake in tools like this, so the answer is "inspect this page first".
 */
public record UnresolvedTargetResponse(String stepId, String page, String element) {}
