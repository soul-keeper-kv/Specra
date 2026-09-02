/**
 * The `execute` job against a real browser and a real page.
 *
 * This is the milestone's "done when", tested rather than asserted: a generated project runs,
 * a passing test is reported as passing, and a *failing* one comes back naming the IR step it
 * failed on — which is the whole reason each step is wrapped in `test.step`.
 *
 * The page under test is served from this process rather than fetched, so the suite has no
 * network dependency and the assertions are about the runner, not about somebody's staging box.
 */

import { createServer, type Server } from "node:http";
import {
  existsSync,
  mkdirSync,
  mkdtempSync,
  rmSync,
  symlinkSync,
  writeFileSync,
} from "node:fs";
import type { AddressInfo } from "node:net";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { afterAll, beforeAll, describe, expect, it } from "vitest";

import { generate } from "../codegen/generate.js";
import { formatFiles } from "../codegen/validate.js";
import { options } from "../codegen/fixtures.js";
import type { PageObject } from "../codegen/types.js";
import { discard, execute } from "./run.js";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(HERE, "..", "..", "..", "..");
/** The one package in this repository with the engine installed. */
const ENGINE = path.join(REPO, "tests", "e2e", "node_modules");

const HTML = `<!doctype html>
<html lang="en">
  <body>
    <h1>Welcome</h1>
    <button id="go">Continue</button>
  </body>
</html>`;

let site: Server;
let baseUrl: string;
let projectDir: string;

/** A page object whose locators really match the served page. */
const PAGES: PageObject[] = [
  {
    name: "HomePage",
    route: "/",
    elements: [
      { name: "heading", strategy: "role", value: "heading", name_: "Welcome" },
      { name: "missing", strategy: "testId", value: "not-on-this-page" },
    ],
  },
];

function irFor(steps: unknown[]): Record<string, unknown> {
  return {
    irVersion: 1,
    name: "The home page loads",
    steps,
  };
}

beforeAll(async () => {
  site = createServer((_request, response) => {
    response.writeHead(200, { "content-type": "text/html" });
    response.end(HTML);
  });
  await new Promise<void>((resolve) => site.listen(0, "127.0.0.1", resolve));
  baseUrl = `http://127.0.0.1:${(site.address() as AddressInfo).port}`;

  projectDir = mkdtempSync(path.join(tmpdir(), "specra-execute-"));
  // The engine, rather than a two-minute `npm install` inside every test run. `execute` skips
  // installing when node_modules is present, which is exactly the path a warm worker takes.
  symlinkSync(ENGINE, path.join(projectDir, "node_modules"), "junction");
}, 120_000);

afterAll(async () => {
  await new Promise<void>((resolve) => site.close(() => resolve()));
  rmSync(projectDir, { recursive: true, force: true });
});

/** Writes a generated project into the shared directory, formatted as the job server sends it. */
async function materialise(steps: unknown[]): Promise<void> {
  const result = generate({
    model: irFor(steps) as never,
    pages: PAGES,
    options: options({
      reference: "TC-1",
      scaffold: true,
      projectName: "execute-fixture",
      browsers: ["chromium"],
    }),
  });

  for (const file of await formatFiles(result.files)) {
    const full = path.join(projectDir, file.path);
    mkdirSync(path.dirname(full), { recursive: true });
    writeFileSync(full, file.contents);
  }
}

const PASSING = [
  { id: "s1", sourceStepIds: ["ts-1"], action: "navigate", target: { page: "HomePage" } },
  {
    id: "s2",
    sourceStepIds: ["ts-2"],
    action: "assert",
    target: { page: "HomePage", element: "heading" },
    assertion: { condition: "visible" },
  },
];

describe("executing a generated project", () => {
  it("runs it against a real page and reports the pass", async () => {
    await materialise(PASSING);

    const result = await execute({
      projectDir,
      baseUrl,
      browsers: ["chromium"],
      isolation: "process",
      timeoutMs: 120_000,
    });
    try {
      expect(result.errorMessage).toBeUndefined();
      expect(result.status).toBe("PASSED");
      expect(result.items).toHaveLength(1);

      const item = result.items[0]!;
      expect(item.status).toBe("PASSED");
      expect(item.browser).toBe("chromium");
      expect(item.specPath).toMatch(/the-home-page-loads\.spec\.ts$/);
      expect(item.title).toBe("The home page loads");
      expect(item.durationMs).toBeGreaterThan(0);
    } finally {
      discard(result.outputDir);
    }
  }, 300_000);

  /**
   * The assertion the whole milestone turns on: a failure names the IR step, so the UI can
   * highlight the manual step the user wrote beside the generated line that broke.
   */
  it("names the IR step a failure landed on, and keeps the evidence", async () => {
    await materialise([
      ...PASSING,
      {
        id: "s3",
        sourceStepIds: ["ts-3"],
        action: "assert",
        // Deliberately not on the page: this is what a locator that has drifted looks like.
        target: { page: "HomePage", element: "missing" },
        assertion: { condition: "visible" },
      },
    ]);

    const result = await execute({
      projectDir,
      baseUrl,
      browsers: ["chromium"],
      isolation: "process",
      timeoutMs: 120_000,
    });
    try {
      // FAILED, not ERROR: the test ran and an assertion did not hold, which is the only
      // outcome worth asking a model to analyse.
      expect(result.status).toBe("FAILED");

      const item = result.items[0]!;
      expect(item.status).toBe("FAILED");
      expect(item.failedStepId).toBe("s3");
      expect(item.errorMessage).toBeTruthy();
      // Terminal colour codes are stripped on the way out; the API stores text.
      expect(item.errorMessage).not.toMatch(/\[/);
      expect(item.errorType).toBe("LOCATOR_TIMEOUT");
      // A failure keeps its trace, which is what M7 will read.
      expect(item.artifacts.some((artifact) => artifact.kind === "TRACE")).toBe(true);
      for (const artifact of item.artifacts) {
        expect(existsSync(path.join(result.outputDir, artifact.path))).toBe(true);
      }
    } finally {
      discard(result.outputDir);
    }
  }, 300_000);

  it("injects variables into the suite without them reaching the repository", async () => {
    await materialise(PASSING);

    const result = await execute({
      projectDir,
      baseUrl,
      browsers: ["chromium"],
      variables: { QA_PASSWORD: "hunter2" },
      isolation: "process",
      timeoutMs: 120_000,
    });
    try {
      expect(result.status).toBe("PASSED");
      // The value was passed to the child process, never written into the working copy.
      expect(existsSync(path.join(projectDir, ".env"))).toBe(false);
      expect(JSON.stringify(result)).not.toContain("hunter2");
    } finally {
      discard(result.outputDir);
    }
  }, 300_000);
});
