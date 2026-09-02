import { describe, expect, it } from "vitest";

import type { RawElement } from "./collect.js";
import { planElements } from "./plan.js";
import { rankCandidates, scoreCandidate } from "./score.js";

/**
 * The locator planner, without a browser.
 *
 * This is the half of inspection that carries the product's judgement — which of the ways to
 * address an element is the one that will still work next month — so it is deliberately pure and
 * tested directly. The browser half only reports facts.
 */

function element(overrides: Partial<RawElement> = {}): RawElement {
  return {
    tag: "button",
    role: "button",
    accessibleName: "Sign in",
    cssPath: "form > button",
    matches: { role: 1, css: 1 },
    ...overrides,
  };
}

describe("scoring a candidate", () => {
  /**
   * The gate, not a weight. A locator matching two nodes is not a worse locator — it is a broken
   * one, and scoring it 0.4 would let it win when nothing else was found.
   */
  it("scores anything that is not unique at zero", () => {
    expect(scoreCandidate("testId", "submit", 2)).toBe(0);
    expect(scoreCandidate("testId", "submit", 0)).toBe(0);
    expect(scoreCandidate("testId", "submit", 1)).toBeGreaterThan(0);
  });

  it("prefers the strategies 02-test-model-ir.md ranks higher", () => {
    const testId = scoreCandidate("testId", "submit", 1);
    const role = scoreCandidate("role", "button", 1);
    const text = scoreCandidate("text", "Sign in", 1);
    const css = scoreCandidate("css", "#submit", 1);
    const xpath = scoreCandidate("xpath", "//button", 1);

    expect(testId).toBeGreaterThan(role);
    expect(role).toBeGreaterThan(text);
    expect(text).toBeGreaterThan(css);
    expect(css).toBeGreaterThan(xpath);
  });

  /** A class the build wrote is a class the next build rewrites. */
  it("distrusts a value that looks machine-generated", () => {
    expect(scoreCandidate("css", "css-1x7f9k", 1)).toBeLessThan(
      scoreCandidate("css", "#submit", 1),
    );
    expect(scoreCandidate("testId", "btn-a1b2c3d4e5", 1)).toBeLessThan(
      scoreCandidate("testId", "submit", 1),
    );
  });

  /** Stable as English, volatile as data: today's total is not tomorrow's. */
  it("distrusts text that carries a value rather than a label", () => {
    expect(scoreCandidate("text", "$42.00", 1)).toBeLessThan(
      scoreCandidate("text", "Sign in", 1),
    );
    expect(scoreCandidate("text", "12 results", 1)).toBeLessThan(
      scoreCandidate("text", "Sign in", 1),
    );
  });

  /**
   * A selector that walks the tree describes where an element sits, and where it sits is exactly
   * what a redesign changes.
   */
  it("distrusts a CSS path that depends on structure", () => {
    expect(scoreCandidate("css", "div > div:nth-child(3) > button", 1)).toBeLessThan(
      scoreCandidate("css", "#submit", 1),
    );
  });

  /** Two inspections of an unchanged page must produce the same page object. */
  it("ranks deterministically when scores tie", () => {
    const candidates = [
      { strategy: "css" as const, value: "#b", matches: 1, score: 0.4 },
      { strategy: "css" as const, value: "#a", matches: 1, score: 0.4 },
    ];
    // The same order whichever way they arrived: DOM order must not reach the page object, or an
    // application that only reordered its markup would appear to have changed.
    expect(rankCandidates(candidates).map((c) => c.value)).toEqual(["#a", "#b"]);
    expect(rankCandidates([...candidates].reverse()).map((c) => c.value)).toEqual(["#a", "#b"]);
  });
});

describe("planning a page", () => {
  it("puts the strongest candidate first, so the caller takes [0] and [1]", () => {
    const { elements } = planElements([
      element({ testId: "submit", matches: { testId: 1, role: 1, css: 1 } }),
    ]);

    expect(elements).toHaveLength(1);
    expect(elements[0]!.candidates[0]!.strategy).toBe("testId");
    expect(elements[0]!.candidates[1]!.strategy).toBe("role");
  });

  /**
   * Reported rather than dropped. "Three buttons I cannot tell apart" is something a person can
   * fix; silence leaves them wondering why their button never appeared.
   */
  it("reports an element no strategy can address uniquely", () => {
    const { elements, ambiguous } = planElements([
      element({ accessibleName: "Delete", matches: { role: 3, css: 3 } }),
    ]);

    expect(elements).toEqual([]);
    expect(ambiguous).toEqual(['button "Delete"']);
  });

  it("names an element the way a person would have", () => {
    const { elements } = planElements([
      element({ accessibleName: "Sign in" }),
      element({
        tag: "input",
        role: "textbox",
        accessibleName: "Email address",
        cssPath: "#email",
        matches: { role: 1, css: 1 },
      }),
    ]);

    expect(elements.map((e) => e.name)).toEqual(["signInButton", "emailAddressInput"]);
  });

  /** Do not produce `submitButtonButton`. */
  it("does not repeat a suffix the name already carries", () => {
    const { elements } = planElements([element({ accessibleName: "Submit button" })]);
    expect(elements[0]!.name).toBe("submitButton");
  });

  /**
   * A page object cannot have two members of one name, and a generation that fails to compile
   * for an invisible reason is worse than an ugly identifier.
   */
  it("disambiguates two elements that would share a name", () => {
    const { elements } = planElements([
      element({ accessibleName: "Delete", testId: "row-1", matches: { testId: 1, role: 2 } }),
      element({ accessibleName: "Delete", testId: "row-2", matches: { testId: 1, role: 2 } }),
    ]);

    expect(elements.map((e) => e.name)).toEqual(["deleteButton", "deleteButton2"]);
  });

  /** An identifier may not start with a digit — `2faCode` would not compile. */
  it("produces a valid identifier from a name that starts with a digit", () => {
    const { elements } = planElements([
      element({ accessibleName: "2FA code", role: "textbox", matches: { role: 1, css: 1 } }),
    ]);

    expect(elements[0]!.name).toMatch(/^[A-Za-z]/);
  });

  it("falls back to a name when there is no accessible one", () => {
    const { elements } = planElements([
      element({
        accessibleName: undefined,
        placeholder: "Search",
        matches: { placeholder: 1, css: 1 },
      }),
    ]);

    expect(elements[0]!.name).toBe("searchButton");
    expect(elements[0]!.candidates[0]!.strategy).toBe("placeholder");
  });

  /** `role` is only addressable with its accessible name; the pair is what the code emits. */
  it("carries the accessible name alongside a role candidate", () => {
    const { elements } = planElements([element()]);
    const role = elements[0]!.candidates.find((c) => c.strategy === "role");

    expect(role?.value).toBe("button");
    expect(role?.name).toBe("Sign in");
  });
});
