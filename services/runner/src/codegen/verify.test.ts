/**
 * The gate `06-execution.md` describes: a generation that does not compile is never proposed.
 *
 * The valuable test here is the negative one. That a good generation passes is also asserted by
 * `portability.test.ts`; what nothing else covers is that a *bad* one is actually refused —
 * which is the whole point of running the compiler at all.
 */

import path from "node:path";
import { fileURLToPath } from "node:url";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

import { LOGIN_PAGES, irFixture, options } from "./fixtures.js";
import { generate } from "./generate.js";
import { formatFiles } from "./validate.js";
import { verifyProject } from "./verify.js";
import type { GeneratedFile } from "./types.js";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(HERE, "..", "..", "..", "..");
/** The one place in this repository that has the engine installed. */
const ENGINE_TYPES = path.join(REPO, "tests", "e2e", "node_modules");

const originalEngineTypes = process.env.SPECRA_ENGINE_TYPES;

beforeEach(() => {
  process.env.SPECRA_ENGINE_TYPES = ENGINE_TYPES;
});

afterEach(() => {
  if (originalEngineTypes === undefined) {
    delete process.env.SPECRA_ENGINE_TYPES;
  } else {
    process.env.SPECRA_ENGINE_TYPES = originalEngineTypes;
  }
});

async function loginProject(): Promise<GeneratedFile[]> {
  const result = generate({
    model: irFixture("login"),
    pages: LOGIN_PAGES,
    options: options({
      reference: "TC-104",
      area: "auth",
      scaffold: true,
      projectName: "acme-e2e",
      browsers: ["chromium"],
    }),
  });
  return formatFiles(result.files);
}

describe("verifying a generation", () => {
  it("passes a project the adapter actually produced", async () => {
    const { ok, skipped, problems } = await verifyProject(await loginProject());

    expect(problems.map((p) => `${p.path} ${p.message}`)).toEqual([]);
    expect(skipped).toBe(false);
    expect(ok).toBe(true);
  }, 180_000);

  /**
   * A type error the compiler must catch. The spec calls a method no page object has — exactly
   * what a drifted template or an IR naming an element nobody inspected would produce.
   */
  it("refuses a project that does not typecheck", async () => {
    const files = await loginProject();
    const spec = files.find((file) => file.role === "SPEC");
    expect(spec).toBeDefined();

    const broken = files.map((file) =>
      file === spec
        ? { ...file, contents: `${file.contents}\nconst n: number = "not a number";\n` }
        : file,
    );

    const { ok, skipped, problems } = await verifyProject(broken);

    expect(skipped).toBe(false);
    expect(ok).toBe(false);
    // Named, not counted: the API shows the user which generated file objected.
    expect(problems[0]?.path).toBe(spec!.path);
    expect(problems[0]?.message).toContain("error TS");
  }, 180_000);

  /**
   * A deployment with no engine types must not silently report every generation as verified —
   * but it must not fail every generation either. Skipped is its own answer.
   */
  it("reports itself skipped rather than passing when the engine types are absent", async () => {
    delete process.env.SPECRA_ENGINE_TYPES;

    const { ok, skipped, problems } = await verifyProject(await loginProject());

    expect(skipped).toBe(true);
    expect(ok).toBe(true);
    expect(problems).toEqual([]);
  }, 60_000);
});
