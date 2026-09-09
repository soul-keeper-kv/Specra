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

import { spawn } from "node:child_process";
import { createRequire } from "node:module";
import path from "node:path";
import { pathToFileURL } from "node:url";

import { COLLECT_SCRIPT, INTERACTIVE, type RawElement } from "../../inspect/collect.js";
import type { InspectRequest } from "../../inspect/types.js";

const DEFAULT_TIMEOUT_MS = 30_000;

/**
 * How long to keep waiting for a page to put something interactive on screen.
 *
 * Its own budget rather than the request's: the whole timeout covers navigation too, and a
 * client-rendered page that never renders should be reported quickly rather than after thirty
 * seconds of an empty document. Capped by the request timeout at the call site.
 */
const SETTLE_BUDGET_MS = 10_000;

/** How often to look. Short enough to add nothing perceptible once the page is up. */
const SETTLE_POLL_MS = 100;

/**
 * How long the count must hold still before it counts as settled.
 *
 * A framework paints in more than one commit — the first input can appear a frame before the
 * rest of the form — so collecting on the first non-zero count would read a half-built page.
 */
const SETTLE_STABLE_MS = 400;

/**
 * esbuild's name helper, as identity.
 *
 * Source text rather than a function, because a function passed through `evaluate` would itself
 * be transpiled and would itself reference `__name`.
 *
 * A *statement*, not an arrow expression: `evaluate` evaluates the text and returns its value, so
 * a bare `() => {…}` is a function that is defined, never called, and assigns nothing.
 */
const NAME_SHIM = "globalThis.__name = globalThis.__name || ((target) => target);";

/** Downloading a browser is a one-off of roughly a hundred megabytes; it needs its own budget. */
const BROWSER_DOWNLOAD_TIMEOUT_MS = 10 * 60 * 1000;

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
  /** The string form: source text the page parses itself, which no transpiler rewrites. */
  evaluate(source: string): Promise<unknown>;
  addInitScript(source: string): Promise<void>;
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

  // Installing the packages does not download the browser binaries — that is a separate step
  // the engine performs on demand. A working copy can therefore resolve the engine and still
  // have nothing to launch, which surfaced as a raw "Executable doesn't exist" naming a cache
  // path the user has no reason to recognise.
  const browser = await launch(chromium, request.projectDir);
  try {
    const context = await browser.newContext();
    const page = await context.newPage();
    await page.goto(request.url, { waitUntil: "domcontentloaded", timeout });

    await settle(page, Math.min(SETTLE_BUDGET_MS, timeout));

    const testIdAttribute = request.testIdAttribute ?? "data-testid";
    // The collect script is serialised and re-parsed in the page, which means it arrives as
    // whatever the *build tool* produced rather than as it was written. esbuild — under tsx in
    // development and under any bundler later — wraps each inner function in a `__name()` call
    // so stack traces keep their names. That helper is injected per module in Node and simply
    // does not exist in a browser, so the script died with `ReferenceError: __name is not`
    // `defined` before reading a single element. Defining it as identity costs nothing and keeps
    // the collect script free of any knowledge that a transpiler exists.
    await page.addInitScript(NAME_SHIM);
    await page.evaluate(NAME_SHIM);

    const elements = await page.evaluate(COLLECT_SCRIPT, testIdAttribute);

    return { url: page.url(), title: await page.title(), elements };
  } finally {
    await browser.close();
  }
}

/**
 * Waits until the page has put its interactive elements on screen and stopped adding to them.
 *
 * This replaced a fixed 500ms pause, which was wrong for the applications this tool exists for.
 * A client-rendered page commonly has *nothing* interactive at `domcontentloaded` — a real one
 * measured here had zero nodes at 500ms and five at two seconds — so a fixed wait returned an
 * empty inspection, and an empty inspection is indistinguishable from a page with no elements.
 * The user is told nothing was found, on a page full of things to find.
 *
 * Two conditions rather than one. **Something appeared** rules out the empty read. **The count
 * held still** rules out the half-built one, because a framework paints in more than one commit.
 *
 * Returning on the budget rather than throwing is deliberate: a page with genuinely nothing
 * interactive on it is a legitimate answer, and `plan.ts` already reports an empty result as
 * an empty result. Refusing here would turn a static page into an error.
 */
async function settle(page: EnginePage, budgetMs: number): Promise<void> {
  const deadline = Date.now() + budgetMs;
  let previous = -1;
  let stableSince = 0;

  while (Date.now() < deadline) {
    // The string form for the same reason the collect script uses it: a function passed here
    // would be transpiled, and the selector is shared with the collector so the wait and the
    // read agree about what they are watching.
    const count = Number(
      await page.evaluate(`document.querySelectorAll(${JSON.stringify(INTERACTIVE)}).length`),
    );

    if (count > 0 && count === previous) {
      if (stableSince === 0) {
        stableSince = Date.now();
      } else if (Date.now() - stableSince >= SETTLE_STABLE_MS) {
        return;
      }
    } else {
      stableSince = 0;
    }

    previous = count;
    await page.waitForTimeout(SETTLE_POLL_MS);
  }
}

/**
 * Launches the browser, downloading it once if the project has never done so.
 *
 * The engine's own CLI does the download, so the version installed is the one the project
 * depends on. Only chromium: inspection reads a DOM, and which engine renders it does not
 * change what is on the page — a run is where the browser matrix matters.
 */
async function launch(
  chromium: EngineBrowserType,
  projectDir: string | undefined,
): Promise<EngineBrowser> {
  try {
    return await chromium.launch({ headless: true });
  } catch (error) {
    if (!isMissingBrowser(error)) {
      throw error;
    }
    const failure = await installBrowser(projectDir ?? process.cwd());
    if (failure) {
      throw new Error(failure);
    }
    return chromium.launch({ headless: true });
  }
}

/** The engine's own words for it; the message is stable and the error carries no code. */
function isMissingBrowser(error: unknown): boolean {
  return (
    error instanceof Error &&
    /Executable doesn't exist|please run the following command to download/i.test(error.message)
  );
}

/** `playwright install chromium`, run as a JS file so `shell: false` holds on every platform. */
function installBrowser(projectDir: string): Promise<string | null> {
  return new Promise((resolve) => {
    let cli: string;
    try {
      cli = createRequire(path.join(projectDir, "package.json")).resolve("playwright/cli.js");
    } catch {
      resolve("the browser could not be downloaded: this project has no engine CLI to run it");
      return;
    }

    const child = spawn(process.execPath, [cli, "install", "chromium"], {
      cwd: projectDir,
      env: { ...process.env, CI: "1" },
      shell: false,
      stdio: ["ignore", "pipe", "pipe"],
    });

    let output = "";
    const capture = (chunk: Buffer) => {
      output += chunk.toString();
    };
    child.stdout.on("data", capture);
    child.stderr.on("data", capture);

    const timer = setTimeout(() => child.kill("SIGKILL"), BROWSER_DOWNLOAD_TIMEOUT_MS);
    child.on("error", (spawnError) => {
      clearTimeout(timer);
      resolve(`the browser could not be downloaded: ${spawnError.message}`);
    });
    child.on("close", (code) => {
      clearTimeout(timer);
      resolve(
        code === 0
          ? null
          : `the browser could not be downloaded:
${output.trim().slice(0, 2000)}`,
      );
    });
  });
}

/**
 * The engine, from the project that already depends on it.
 *
 * Resolved rather than imported for the containment reason above. A project without it gets a
 * message naming the fix, not a stack trace about a missing module.
 */
async function engineBrowser(projectDir: string | undefined): Promise<EngineBrowserType> {
  const from = projectDir ?? process.cwd();
  let resolved: string;
  try {
    resolved = createRequire(path.join(from, "package.json")).resolve("playwright");
  } catch {
    throw new Error(
      "the browser engine is not installed in this project: run `npm install` in the repository" +
        " before inspecting a page",
    );
  }

  // `pathToFileURL`, not the path itself. `resolve()` answers with a filesystem path, and on
  // Windows that is `C:\\…`, which the ESM loader reads as a URL with the scheme "c:" and
  // refuses. An earlier version let that land in the catch above, so a machine with the engine
  // correctly installed was told to install it — and following that advice changed nothing.
  const engine = (await import(pathToFileURL(resolved).href)) as EngineModule;

  // The engine is CommonJS, so an ESM `import()` of it puts the real exports on `default` and
  // synthesises named ones only for what it can statically see — which does not include
  // `chromium`. Reading the named export alone found `undefined` and failed one call later, at
  // `.launch()`, naming neither the module nor the reason.
  const chromium = engine.chromium ?? engine.default?.chromium;
  if (!chromium) {
    throw new Error(
      "the browser engine was found but exposes no chromium build: the project's playwright" +
        " install looks incomplete",
    );
  }
  return chromium;
}

/** Both shapes the engine can arrive in, so the interop is read rather than assumed. */
interface EngineModule {
  chromium?: EngineBrowserType;
  default?: { chromium?: EngineBrowserType };
}
