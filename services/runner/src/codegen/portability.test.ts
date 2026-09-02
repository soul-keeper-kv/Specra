/**
 * The claim the whole product rests on, tested rather than asserted: a scaffolded project
 * stands on its own.
 *
 * "If Specra disappeared tomorrow, every user still has a working automation project"
 * (07-git.md). So this writes a real generated project to a temp directory and runs the real
 * TypeScript compiler over it against the real `@playwright/test` types. A projection that does
 * not compile is an adapter bug, and this is where it gets caught — not in the user's
 * repository, and never by retrying a model.
 *
 * `tests/e2e` supplies the Playwright types; nothing else in this repo has them, and the runner
 * deliberately does not depend on the engine outside its adapter.
 */

import { execFileSync } from "node:child_process";
import { createRequire } from "node:module";
import { existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
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

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(HERE, "..", "..", "..", "..");
/** The one place in this repository that has the engine's types installed. */
const ENGINE_TYPES = path.join(REPO, "tests", "e2e", "node_modules");
/** Resolved, not guessed: pnpm hoists typescript into a content-addressed store path. */
const TSC = createRequire(import.meta.url).resolve("typescript/bin/tsc");

const workdir = mkdtempSync(path.join(tmpdir(), "specra-generated-"));

afterAll(() => rmSync(workdir, { recursive: true, force: true }));

/**
 * Both cases into one project, because that is how a real repository accumulates: the login case
 * scaffolds it, and the checkout case adds a second spec, more page objects, and the flows.
 *
 * The checkout case is the one that matters here — a flow that referenced a page object it never
 * constructed compiled fine in isolation and not at all in a project, which is precisely the
 * class of bug a golden file cannot see and the compiler can.
 */
function materialise(): string {
  const generations = [
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
  ];

  for (const result of generations) {
    for (const file of result.files) {
      const full = path.join(workdir, file.path);
      mkdirSync(path.dirname(full), { recursive: true });
      writeFileSync(full, file.contents);
    }
  }

  // A generated project carries no tsconfig (07-git.md's tree has none), so supply the one a
  // Playwright project would, pointed at the engine types this repo already has.
  writeFileSync(
    path.join(workdir, "tsconfig.json"),
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
  return workdir;
}

describe("the generated project stands on its own", () => {
  const engineAvailable = existsSync(path.join(ENGINE_TYPES, "@playwright", "test"));

  it("typechecks with the real compiler against the real engine types", () => {
    if (!engineAvailable) {
      // Never silently pass: say why, so a green run is not mistaken for a verified one.
      throw new Error(
        `The engine types are not installed at ${ENGINE_TYPES}. Run pnpm install at the repo root.`,
      );
    }

    const dir = materialise();
    let output = "";
    try {
      execFileSync(process.execPath, [TSC, "--noEmit", "--project", "tsconfig.json"], {
        cwd: dir,
        encoding: "utf8",
        stdio: ["ignore", "pipe", "pipe"],
      });
    } catch (error) {
      const failure = error as { stdout?: string; stderr?: string };
      output = `${failure.stdout ?? ""}${failure.stderr ?? ""}`;
    }

    expect(output).toBe("");
  }, 120_000);
});
