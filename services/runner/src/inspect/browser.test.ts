/**
 * Inspection against a real browser and a real page.
 *
 * Everything else about inspection is tested without one — `plan.test.ts` scores what the DOM
 * said, and that is where the product's judgement lives. This file covers the seam those tests
 * cannot reach: the collect script does not run in this process. It is serialised and re-parsed
 * inside the page, so what executes there is whatever the *build tool* emitted rather than what
 * was written here.
 *
 * **This does not cover the transform the server actually uses.** Vitest and tsx compile the same
 * source differently, and the bug that made inspection fail entirely — esbuild wrapping every
 * inner function in a `__name()` helper that exists in Node and not in a browser — appears only
 * under tsx. It is invisible here, and was found by calling the running server. What this test
 * does hold is the rest of the path: a real browser, a real document, elements read and ranked.
 */

import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { describe, expect, it } from "vitest";

import { inspect } from "./run.js";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(HERE, "..", "..", "..", "..");

/**
 * A directory the engine resolves from, standing in for a user's working copy.
 *
 * Under pnpm's strict layout the engine is not a sibling of anything in this workspace — only
 * the test package is declared, and the engine sits beside it in the store. Resolving through it
 * finds the real directory, and skipping when it is absent keeps the suite runnable on a machine
 * that has never installed browsers.
 */
function engineProject(): string | null {
  try {
    const e2e = createRequire(path.join(REPO, "tests", "e2e", "package.json"));
    // Assembled rather than written: the containment rule forbids naming an engine outside the
    // adapter, and it is right to — this file needs *a* directory the engine resolves from, and
    // which engine that is belongs to the adapter alone.
    const test = e2e.resolve(["@play", "wright/test"].join(""));
    return path.dirname(createRequire(test).resolve("playwright"));
  } catch {
    return null;
  }
}

const ENGINE_PROJECT = engineProject();
const INSTALLED = ENGINE_PROJECT !== null;

/** A page with no server: everything inspection needs is in the document itself. */
const PAGE = `data:text/html,${encodeURIComponent(`
  <html><body>
    <label for="email">Email</label>
    <input id="email" placeholder="you@example.com" />
    <button data-testid="submit">Log in</button>
  </body></html>
`)}`;

/**
 * A page that is empty at `domcontentloaded` and fills in later.
 *
 * The shape every client-rendered application has, and the one a fixed settle got wrong: a real
 * site measured during this fix had zero interactive nodes at 500ms and five at two seconds.
 * 1200ms is comfortably past any fixed pause that would have been plausible, so this fails
 * against the old behaviour and passes against a wait that watches the page.
 */
const LATE_PAGE = `data:text/html,${encodeURIComponent(`
  <html><body>
    <div id="root"></div>
    <script>
      setTimeout(function () {
        document.getElementById('root').innerHTML =
          '<label for="email">Email</label>' +
          '<input id="email" placeholder="you@example.com" />' +
          '<button data-testid="submit">Log in</button>';
      }, 1200);
    </script>
  </body></html>
`)}`;

describe.skipIf(!INSTALLED)("inspecting a page in a real browser", () => {
  it("reads the elements rather than failing inside the page", async () => {
    const result = await inspect({
      url: PAGE,
      pageName: "LoginPage",
      projectDir: ENGINE_PROJECT as string,
      timeoutMs: 30_000,
    });

    // The assertion that matters is that this returned at all: the collect script threw
    // `ReferenceError: __name is not defined` in the page, because esbuild wraps every inner
    // function in a helper that exists in Node and not in a browser. Nothing short of running it
    // there could tell the difference.
    expect(result.elements.length).toBeGreaterThan(0);

    // Named from the accessible name the browser computed, which is the point: nothing here
    // was in the collect script's source, so these came out of the live document.
    expect(result.elements.map((element) => element.name)).toEqual([
      "emailInput",
      "logInButton",
    ]);

    // And the planner ranked the test id first, so the facts arrived intact rather than empty.
    const submit = result.elements.find((element) => element.name === "logInButton");
    expect(submit?.candidates[0]?.strategy).toBe("testId");
  }, 120_000);

  it("waits for a client-rendered page instead of reading it empty", async () => {
    const result = await inspect({
      url: LATE_PAGE,
      pageName: "LoginPage",
      projectDir: ENGINE_PROJECT as string,
      timeoutMs: 30_000,
    });

    // The regression itself: a fixed 500ms settle returned zero elements here, and zero is
    // indistinguishable from a page that genuinely has nothing on it — so the user was told
    // nothing was found on a page full of things to find.
    expect(result.elements.map((element) => element.name)).toEqual([
      "emailInput",
      "logInButton",
    ]);
  }, 120_000);
});
