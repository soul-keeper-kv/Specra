import type { LocatorCandidate, LocatorStrategy } from "./types.js";

/**
 * How good a locator is, as a number between 0 and 1.
 *
 * Three factors, in the order 02-test-model-ir.md gives them:
 *
 * - **Uniqueness** — does it match exactly one node. This is a gate, not a weight: a locator
 *   matching two nodes is not a worse locator, it is a broken one, and scoring it 0.4 would let
 *   it win against nothing.
 * - **Stability** — will it survive the next deploy. A `data-testid` was put there on purpose;
 *   a class like `css-1x7f9k` was put there by a build.
 * - **Semantics** — would a human recognise it. "the button called Sign in" reads like the test
 *   case it came from; `div > div:nth-child(3)` does not.
 *
 * The strategy rank carries most of the signal, because the ranking already encodes all three.
 * The penalties below are for the cases where a strategy is nominally good but this particular
 * value is not — a test id that looks generated, text that looks like a timestamp.
 */

/** Base score per strategy: the priority table, normalised. */
const BASE: Record<LocatorStrategy, number> = {
  testId: 1.0,
  role: 0.9,
  label: 0.85,
  placeholder: 0.7,
  text: 0.65,
  altText: 0.6,
  title: 0.55,
  css: 0.4,
  xpath: 0.15,
};

/**
 * Values that look machine-generated, and therefore different on the next build.
 *
 * Deliberately conservative: a false positive here demotes a locator that would have been fine,
 * which costs a little quality. A false negative ships a locator that breaks on Tuesday, which
 * costs the user's trust in the whole tool.
 */
const GENERATED = [
  // emotion/styled-components: `css-1x7f9k`, `sc-bdVaJa`. Base36, not hex — these are hashes of
  // the rule, not of bytes, so restricting the character class to [0-9a-f] misses most of them.
  // Requiring at least one digit is what keeps it off ordinary names like `data-testid`.
  /^\.?[a-z]{2,4}-(?=[a-z0-9]*\d)[a-z0-9]{5,}$/i,
  /\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/i, // a uuid
  /:r[0-9a-z]+:/i, // React 18 useId
  /\b\d{10,}\b/, // a timestamp or a sequence
  /^ember\d+$/i,
  /^ng-tns-c\d+/i,
];

/** Text that is stable as English but volatile as data. */
const VOLATILE_TEXT = [
  /\d{1,2}[:/]\d{2}/, // a time or a date fragment
  /\b\d+\s*(items?|results?|records?)\b/i, // "12 results"
  /[$€£]\s?\d/, // a price
];

export function scoreCandidate(
  strategy: LocatorStrategy,
  value: string,
  matches: number,
): number {
  // Not unique: not a locator. Reported with its score so the caller can say why it lost.
  if (matches !== 1) return 0;

  let score = BASE[strategy];

  if (GENERATED.some((pattern) => pattern.test(value))) {
    // Halved rather than zeroed: a generated-looking test id is still better than an xpath, and
    // some teams really do hash their test ids on purpose.
    score *= 0.5;
  }

  if (
    (strategy === "text" || strategy === "title") &&
    VOLATILE_TEXT.some((p) => p.test(value))
  ) {
    score *= 0.4;
  }

  // Long values are brittle whatever the strategy: a 200-character CSS path encodes the entire
  // document structure, so any change anywhere breaks it.
  if (value.length > 80) score *= 0.7;
  else if (value.length > 40) score *= 0.9;

  // An xpath with positional indices is the most brittle thing we can produce. It stays as a
  // last resort rather than being dropped, because "no locator at all" helps nobody.
  if (strategy === "xpath" && /\[\d+\]/.test(value)) score *= 0.6;

  // A CSS selector that walks the tree is describing where an element sits rather than what it
  // is, and where it sits is exactly what a redesign changes.
  if (strategy === "css" && /[>+~]|:nth-/.test(value)) score *= 0.6;

  return round(score);
}

/** Best first, so the caller can take [0] as the locator and [1] as the fallback. */
export function rankCandidates(candidates: LocatorCandidate[]): LocatorCandidate[] {
  return [...candidates]
    .filter((candidate) => candidate.score > 0)
    .sort((a, b) => {
      if (b.score !== a.score) return b.score - a.score;
      // A tie is broken by the strategy table, then the shorter value, then the value itself.
      // The last comparison looks redundant and is not: without it two candidates of the same
      // strategy and length fall back to input order, which is DOM order — so a page that
      // rendered its buttons in a different sequence would produce a different page object for
      // an application that had not changed. Ordering must be total to be deterministic.
      const rank = BASE[b.strategy] - BASE[a.strategy];
      if (rank !== 0) return rank;
      if (a.value.length !== b.value.length) return a.value.length - b.value.length;
      return a.value < b.value ? -1 : a.value > b.value ? 1 : 0;
    });
}

/** Two decimals: the number is a judgement, and eleven digits would imply otherwise. */
function round(value: number): number {
  return Math.round(value * 100) / 100;
}
