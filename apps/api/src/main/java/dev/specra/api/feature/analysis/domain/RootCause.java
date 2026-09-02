package dev.specra.api.feature.analysis.domain;

/**
 * What the evidence says went wrong (the table in 08-ai-pipeline.md).
 *
 * <p>The set is closed on purpose. A free-text cause would be unqueryable, untranslatable, and —
 * worse — would let a model invent a category that sounds like a fix is warranted when it is not.
 */
public enum RootCause {
  /** The element moved or was renamed. The page object is wrong; the test is not. */
  LOCATOR_DRIFT,

  /** A race: the assertion ran before the application had settled. */
  TIMING,

  /** The application legitimately changed. The IR needs review, not the code. */
  APPLICATION_CHANGED,

  /** The environment is missing a value, or holds a stale one. */
  TEST_DATA,

  /**
   * The application under test regressed.
   *
   * <p>The row that makes this feature honest. It is the answer that proposes no diff — invariant 7
   * — and a classifier that cannot reach it will eventually "repair" a real regression by loosening
   * the assertion that caught it.
   */
  PRODUCT_BUG,

  /** The evidence did not support any of the above. Said plainly rather than guessed at. */
  UNKNOWN;

  /**
   * Whether a repair proposal may be offered at all.
   *
   * <p>Enforced here rather than at the call site so every caller inherits it: a product bug is
   * fixed in the application, and an unclassifiable failure has nothing to base a patch on.
   */
  public boolean repairable() {
    return this != PRODUCT_BUG && this != UNKNOWN;
  }
}
