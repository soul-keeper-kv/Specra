package dev.specra.api.core.testmodel;

/** A raw locator. The escape hatch, recorded as debt — never the first choice. */
public record Selector(SelectorStrategy strategy, String value) {}
