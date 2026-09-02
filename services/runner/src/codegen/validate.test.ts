/**
 * The other two thirds of M3's "done when": the generated project passes `tsc` (that is
 * `portability.test.ts`), `eslint` and `prettier --check`.
 *
 * These run the real tools over the real output. A rule that only checks our own idea of the
 * output would pass while the user's editor reformatted every generated file on save.
 */

import { execFileSync } from "node:child_process";
import { existsSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { afterAll, describe, expect, it } from "vitest";

import {
  CHECKOUT_PAGES,
  CLEAR_CART_FLOW,
  LOGIN_FLOW,
  LOGIN_PAGES,
  irFixture,
  options,
} from "./fixtures.js";
import { generate } from "./generate.js";
import {
  checkFormatting,
  formatFiles,
  lintProject,
  materialise,
  writeLintConfig,
} from "./validate.js";
import type { GeneratedFile } from "./types.js";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(HERE, "..", "..", "..", "..");
/** The one place in this repository with the engine's types installed. */
const ENGINE_TYPES = path.join(REPO, "tests", "e2e", "node_modules");

const workdir = mkdtempSync(path.join(tmpdir(), "specra-validate-"));
afterAll(() => rmSync(workdir, { recursive: true, force: true }));

/**
 * The same two-generation project `portability.test.ts` builds: one repository, accumulated —
 * and delivered the way the job server delivers it, which is after prettier.
 */
async function generatedProject(): Promise<GeneratedFile[]> {
  const files = [
    generate({
      model: irFixture("login"),
      pages: LOGIN_PAGES,
      options: options({
        reference: "TC-104",
        area: "auth",
        scaffold: true,
        projectName: "acme-e2e",
        browsers: ["chromium"],
      }),
    }),
    generate({
      model: irFixture("checkout"),
      pages: [...CHECKOUT_PAGES, ...LOGIN_PAGES],
      flows: [
        { name: "login", model: LOGIN_FLOW },
        { name: "clearCart", model: CLEAR_CART_FLOW },
      ],
      options: options({ reference: "TC-77", area: "checkout" }),
    }),
  ].flatMap((result) => result.files);
  return formatFiles(files);
}

describe("the generated project is formatted", () => {
  it("is byte-identical to what prettier would write", async () => {
    const { ok, problems } = await checkFormatting(await generatedProject());

    // Named rather than counted: the message has to say which template drifted.
    expect(problems.map((p) => `${p.path} ${p.message}`)).toEqual([]);
    expect(ok).toBe(true);
  }, 60_000);
});

describe("the generated project lints", () => {
  const engineAvailable = existsSync(path.join(ENGINE_TYPES, "@playwright", "test"));

  it("passes eslint with type information", async () => {
    if (!engineAvailable) {
      // Never silently pass: a green run must not be mistaken for a verified one.
      throw new Error(
        `The engine types are not installed at ${ENGINE_TYPES}. Run pnpm install at the repo root.`,
      );
    }

    materialise(workdir, await generatedProject());

    // A generated project carries no tsconfig (07-git.md's tree has none), so supply the one a
    // Playwright project would — typed linting needs it as much as tsc does.
    const tsconfigPath = path.join(workdir, "tsconfig.json");
    writeFileSync(
      tsconfigPath,
      `${JSON.stringify(
        {
          compilerOptions: {
            target: "ES2022",
            lib: ["ES2022", "DOM"],
            module: "ESNext",
            moduleResolution: "Bundler",
            strict: true,
            noEmit: true,
            esModuleInterop: true,
            skipLibCheck: true,
            typeRoots: [path.join(ENGINE_TYPES, "@types")],
            baseUrl: ".",
            paths: { "*": [path.join(ENGINE_TYPES, "*").replace(/\\/g, "/")] },
          },
          include: ["**/*.ts"],
        },
        null,
        2,
      )}\n`,
    );

    const configPath = writeLintConfig(workdir, tsconfigPath);
    const { ok, problems } = await lintProject(workdir, configPath);

    expect(problems.map((p) => p.message).join("\n")).toBe("");
    expect(ok).toBe(true);
  }, 180_000);
});
