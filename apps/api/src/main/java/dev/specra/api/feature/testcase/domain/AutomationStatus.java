package dev.specra.api.feature.testcase.domain;

/**
 * How far a test case has got down the pipeline (01-domain-model.md).
 *
 * <pre>
 * NOT_AUTOMATED ──► MODELLED ──► GENERATED ──► COMMITTED
 *       ▲                                          │
 *       └──────────────── (code deleted) ──────────┘
 * </pre>
 *
 * <p>"The case changed after its IR was generated" is deliberately not a state here — it is the
 * {@code outOfDate} flag beside it, set on edit and cleared by regeneration, so a stale case does
 * not forget how far it had already got.
 */
public enum AutomationStatus {
  NOT_AUTOMATED,
  MODELLED,
  GENERATED,
  COMMITTED
}
