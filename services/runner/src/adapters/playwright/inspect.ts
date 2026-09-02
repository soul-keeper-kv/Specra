/**
 * Reading a real page, in Playwright's words.
 *
 * The only file that opens a browser to look rather than to run. It produces facts — role,
 * accessible name, label, test id, text, a CSS path — and hands them to the engine-free planner
 * above, which decides which of them makes the best locator.
 *
 * The split matters: the *scoring* is where the product's judgement lives and it must be
 * testable without a browser, so this file collects and never chooses.
 *
 * **The engine is resolved from the user's project, not imported.** This package has no
 * Playwright dependency and must not gain one — the same rule `engineCliPath` follows for
 * execution. A static import here would make the runner's own install pull a browser stack it
 * only needs when a user asks it to look at a page.
 */

import { createRequire } from "node:module";
import path from "node:path";

import { COLLECT_SCRIPT, type RawElement } from "../../inspect/collect.js";
import type { InspectRequest } from "../../inspect/types.js";

const DEFAULT_TIMEOUT_MS = 30_000;

export interface RawPage {
  url: string;
  title: string;
  elements: RawElement[];
}

/** The slice of the engine this file uses, so the `any` from a dynamic resolve stops here. */
interface EngineBrowserType {
  launch(options?: { headless?: boolean }): Promise<EngineBrowser>;
}

interface EngineBrowser {
  newContext(): Promise<EngineContext>;
  close(): Promise<void>;
}

interface EngineContext {
  newPage(): Promise<EnginePage>;
}

interface EnginePage {
  goto(url: string, options?: { waitUntil?: string; timeout?: number }): Promise<unknown>;
  waitForTimeout(ms: number): Promise<void>;
  evaluate<T>(fn: (arg: string) => T, arg: string): Promise<T>;
  url(): string;
  title(): Promise<string>;
}

/**
 * Opens the URL and reports what is interactive on it.
 *
 * `domcontentloaded` rather than `networkidle`: a page with a poll or an open socket never goes
 * idle, and waiting for it would turn "inspect this page" into a timeout on exactly the modern
 * applications this tool exists for. The short settle below covers the client-side render that
 * `domcontentloaded` misses.
 */
export async function readPage(request: InspectRequest): Promise<RawPage> {
  const timeout = request.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  const chromium = await engineBrowser(request.projectDir);

  const browser = await chromium.launch({ headless: true });
  try {
    const context = await browser.newContext();
    const page = await context.newPage();
    await page.goto(request.url, { waitUntil: "domcontentloaded", timeout });

    // One short settle for a framework to paint. Deliberately fixed and deliberately small: this
    // is inspection, not a test, and a page still moving after half a second is one whose
    // locators would be unstable anyway.
    await page.waitForTimeout(500);

    const testIdAttribute = request.testIdAttribute ?? "data-testid";
    const elements = await page.evaluate(COLLECT_SCRIPT, testIdAttribute);

    return { url: page.url(), title: await page.title(), elements };
  } finally {
    await browser.close();
  }
}

/**
 * The engine, from the project that already depends on it.
 *
 * Resolved rather than imported for the containment reason above. A project without it gets a
 * message naming the fix, not a stack trace about a missing module.
 */
async function engineBrowser(projectDir: string | undefined): Promise<EngineBrowserType> {
  const from = projectDir ?? process.cwd();
  try {
    const require_ = createRequire(path.join(from, "package.json"));
    const engine = (await import(require_.resolve("playwright"))) as {
      chromium: EngineBrowserType;
    };
    return engine.chromium;
  } catch {
    throw new Error(
      "the browser engine is not installed in this project: run `npm install` in the repository" +
        " before inspecting a page",
    );
  }
}
