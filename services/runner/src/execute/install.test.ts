/**
 * Installing a working copy, and the two ways it used to fail silently.
 *
 * Both cases here came from a real repository rather than from imagination: a lockfile committed
 * before it had ever been filled in, and an error message whose useful half was cut off.
 */

import { existsSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { afterEach, describe, expect, it } from "vitest";

import { excerpt, installDependencies } from "./install.js";

const directories: string[] = [];

afterEach(() => {
  for (const directory of directories.splice(0)) {
    rmSync(directory, { recursive: true, force: true });
  }
});

function workingCopy(files: Record<string, string>): string {
  const directory = mkdtempSync(path.join(tmpdir(), "specra-install-"));
  directories.push(directory);
  for (const [name, contents] of Object.entries(files)) {
    writeFileSync(path.join(directory, name), contents);
  }
  return directory;
}

/** No dependencies, so the install is a real one that still finishes in seconds. */
const MANIFEST = JSON.stringify({
  name: "e2e-tests",
  version: "0.1.0",
  private: true,
});

describe("installing a working copy", () => {
  it("recovers from a lockfile that does not match the manifest", async () => {
    // The shape that blocked every run against a real repository: a lockfile committed with an
    // empty `packages`, which `npm ci` refuses outright. The fix npm names is `npm install`, and
    // a QA user has no shell in this directory to run it.
    const projectDir = workingCopy({
      "package.json": JSON.stringify({
        ...JSON.parse(MANIFEST),
        devDependencies: { pad: "0.0.1" },
      }),
      "package-lock.json": JSON.stringify({
        name: "e2e-tests",
        lockfileVersion: 3,
        requires: true,
        packages: {},
      }),
    });

    expect(await installDependencies(projectDir)).toBeNull();
    expect(existsSync(path.join(projectDir, "node_modules"))).toBe(true);
  }, 300_000);

  it("says nothing to do when the manifest is missing", async () => {
    const projectDir = workingCopy({ "README.md": "nothing here\n" });

    const failure = await installDependencies(projectDir);

    expect(failure).toContain("no package.json");
  }, 30_000);

  it("skips the install when the dependencies are already there", async () => {
    const projectDir = workingCopy({ "package.json": MANIFEST });
    // A directory rather than a real tree: the check is `node_modules` exists, and a warm worker
    // must not pay for an install it does not need.
    writeFileSync(path.join(projectDir, "package-lock.json"), "not even valid json");
    rmSync(path.join(projectDir, "package-lock.json"));
    mkdtempSync(path.join(projectDir, "node_modules"));

    expect(await installDependencies(projectDir)).toBeNull();
  }, 30_000);
});

describe("the excerpt of a failure", () => {
  it("keeps the head, where npm says what went wrong", () => {
    // npm prints the reason first and then, on a usage error, its whole help text. Keeping the
    // tail kept the flag list and threw away the sentence — which is how an out-of-sync lockfile
    // reached the user as "--allow-file".
    const reason = "npm error `npm ci` can only install packages when your package.json";
    const output = `${reason}\n${"npm error   --allow-file\n".repeat(500)}`;

    const kept = excerpt(output);

    expect(kept).toContain(reason);
    expect(kept.length).toBeLessThan(output.length);
  });

  it("leaves a short message whole", () => {
    expect(excerpt("  npm error ENOENT  ")).toBe("npm error ENOENT");
  });
});
