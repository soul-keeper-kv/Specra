package dev.specra.api.feature.pageobject.domain;

/**
 * How an element is addressed, ranked as 02-test-model-ir.md ranks them.
 *
 * <p>Declaration order is the priority order, and it is load-bearing: the planner scores against
 * it, and a UI listing strategies for a person to choose from lists them best-first by relying on
 * it. Reordering these constants changes what the product prefers.
 *
 * <p>No engine appears here. {@code testId} becomes one call in Playwright and a different one in
 * whatever comes next; that translation lives in {@code services/runner/src/adapters/}.
 */
public enum LocatorStrategy {
  /** A `data-testid` or whatever attribute the project nominates. Put there on purpose. */
  TEST_ID,
  /** An ARIA role plus an accessible name — what a screen reader would use. */
  ROLE,
  LABEL,
  PLACEHOLDER,
  TEXT,
  ALT_TEXT,
  TITLE,
  /** A stable id or a semantic class. */
  CSS,
  /** Last resort, and recorded as debt. */
  XPATH;

  /** The wire form the runner uses: `testId`, `altText`. */
  public String code() {
    String[] parts = name().toLowerCase().split("_");
    StringBuilder text = new StringBuilder(parts[0]);
    for (int index = 1; index < parts.length; index++) {
      text.append(Character.toUpperCase(parts[index].charAt(0))).append(parts[index].substring(1));
    }
    return text.toString();
  }

  /**
   * Parses the runner's form. Unknown is CSS rather than an exception: a locator we cannot classify
   * is still a locator, and refusing the whole inspection over one element would be worse than
   * recording it as the generic strategy.
   */
  public static LocatorStrategy fromCode(String code) {
    for (LocatorStrategy strategy : values()) {
      if (strategy.code().equalsIgnoreCase(code)) {
        return strategy;
      }
    }
    return CSS;
  }
}
