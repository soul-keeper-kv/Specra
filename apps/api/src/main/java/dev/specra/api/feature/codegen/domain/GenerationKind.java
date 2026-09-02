package dev.specra.api.feature.codegen.domain;

/**
 * Where a proposal's files came from.
 *
 * <p>Both kinds live in one table, and deliberately so: a repair is reviewed, applied and committed
 * exactly like a generation, and giving it its own table would mean a second path that writes into
 * a user's repository. One path is what makes "a human approves every write" checkable rather than
 * merely intended.
 */
public enum GenerationKind {
  /** A projection of the IR by the deterministic adapter. No model call. */
  CODE,

  /**
   * A repair of code that already exists, written from a failure's evidence.
   *
   * <p>Not a projection: it patches a committed file rather than deriving one from an IR version,
   * which is why {@code test_model_id} is nullable for these.
   */
  FIX
}
