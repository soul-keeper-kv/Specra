package dev.specra.api.feature.codegen.dto;

/**
 * A step that names a page nobody has inspected.
 *
 * <p>Reported rather than guessed: a model inventing {@code #login-btn} is the largest source of
 * flake in tools like this, so the answer is "inspect this page first".
 */
public record UnresolvedTargetResponse(String stepId, String page, String element) {}
