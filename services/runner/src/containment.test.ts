/**
 * The containment rule, as a test rather than as a paragraph nobody reads.
 *
 * Playwright vocabulary lives in `src/adapters/playwright/` and nowhere else — the same idea as
 * the Java `ArchitectureTest` rule that no class may name an LLM vendor. A leak does not break
 * anything today; it breaks the day a second engine is asked for, which is exactly when nobody
 * remembers this rule existed.
 */

import { readdirSync, readFileSync, statSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { describe, expect, it } from "vitest";

const SRC = path.dirname(fileURLToPath(import.meta.url));
const ADAPTER = path.join(SRC, "adapters", "playwright");

/** Words that only mean something to an execution engine. */
const ENGINE_VOCABULARY = [
  "@playwright/test",
  "getByRole",
  "getByTestId",
  "getByLabel",
  "toBeVisible",
  "playwright.config",
  "cy.",
  "webdriver",
  "selenium",
];

function sourceFiles(dir: string): string[] {
  const found: string[] = [];
  for (const entry of readdirSync(dir)) {
    const full = path.join(dir, entry);
    if (statSync(full).isDirectory()) {
      found.push(...sourceFiles(full));
    } else if (entry.endsWith(".ts")) {
      found.push(full);
    }
  }
  return found;
}

/**
 * The two files that may name the engine without living in the adapter, and why.
 *
 * A list rather than a pattern, so adding one is a deliberate edit somebody reviews — an
 * exemption that matches `*.test.ts` would quietly exempt half the package.
 */
const EXEMPT = new Set([
  // This file: it has to spell the words out to look for them.
  "containment.test.ts",
  // It typechecks the generated project against the real engine types, which means resolving
  // them by name. Checking the projection is not speaking the vocabulary.
  path.join("codegen", "portability.test.ts"),
]);

describe("engine containment", () => {
  const outsideAdapter = sourceFiles(SRC).filter(
    (file) => !file.startsWith(ADAPTER) && !EXEMPT.has(path.relative(SRC, file)),
  );

  it("finds files to check, so a rename cannot make this test vacuous", () => {
    expect(outsideAdapter.length).toBeGreaterThan(3);
    expect(sourceFiles(ADAPTER).length).toBeGreaterThan(3);
  });

  it.each(ENGINE_VOCABULARY)("no file outside the adapter mentions %s", (word) => {
    const offenders = outsideAdapter.filter((file) =>
      readFileSync(file, "utf8").includes(word),
    );
    expect(offenders.map((file) => path.relative(SRC, file))).toEqual([]);
  });
});
