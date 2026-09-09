/**
 * The one thing about `collect.ts` that cannot be checked by running it.
 *
 * `COLLECT_SCRIPT` is serialised into the page, so it may close over nothing from this module —
 * which means the selector it uses cannot be the exported `INTERACTIVE` and has to be a copy.
 * A copy that nothing checks is a copy that drifts, and the drift would be quiet: the adapter
 * waits for one set of nodes and the collector reads another, so inspection settles at the
 * wrong moment and comes back short. This test is what makes the duplication safe to keep.
 */

import { describe, expect, it } from "vitest";

import { COLLECT_SCRIPT, INTERACTIVE } from "./collect.js";

describe("the interactive selector", () => {
  it("is the same in the exported constant and inside the collect script", () => {
    // The script's own source, which is what the browser ends up parsing.
    const source = COLLECT_SCRIPT.toString();

    // Written as a concatenation over three lines in both places, so compare the *values* the
    // pieces make rather than the text: a reformat should not fail this, a changed role should.
    const literals = source.match(/"[^"]*"/g) ?? [];
    const joined = literals.map((piece) => piece.slice(1, -1)).join("");

    expect(joined).toContain(INTERACTIVE);
  });

  it("covers the roles a modern application uses instead of native tags", () => {
    // Not a restatement of the constant — these are the entries whose absence would silently
    // shrink what inspection can see on a component-library page.
    for (const role of ["[role=button]", "[role=textbox]", "[role=combobox]", "[role=link]"]) {
      expect(INTERACTIVE).toContain(role);
    }
  });
});
