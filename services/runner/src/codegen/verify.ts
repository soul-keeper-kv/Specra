/**
 * Typecheck and lint a generation before it is allowed to become a proposal.
 *
 * `06-execution.md` states the rule this file exists to enforce: a generation that does not
 * compile never reaches the user as a proposal. Prettier already runs unconditionally — it is
 * pure and in-memory — but eslint and tsc need a real project on disk with the engine's types
 * resolvable, which is what this does.
 *
 * **Why the engine types are a path and not a dependency.** The runner does not depend on the
 * execution engine's package outside its adapter, and adding it here to typecheck would be the
 * containment rule leaking in through the back door. Instead the location is configuration:
 * `SPECRA_ENGINE_TYPES` names a directory that has the engine installed, and without it
 * verification reports itself as skipped rather than silently passing. Checking the projection
 * is not speaking the vocabulary — the same reasoning `containment.test.ts` already exempts
 * `portability.test.ts` under.
 */

import { execFileSync } from "node:child_process";
import { existsSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import { tmpdir } from "node:os";
import path from "node:path";

import type { GeneratedFile } from "./types.js";
import {
  lintProject,
  materialise,
  writeLintConfig,
  type ValidationProblem,
} from "./validate.js";

/** Where the engine's types live. A deployed runner sets it; in this repo `tests/e2e` has them. */
const ENGINE_TYPES_ENV = "SPECRA_ENGINE_TYPES";

export interface VerificationResult {
  /** False only when a tool actually objected — a skipped check is not a failure. */
  ok: boolean;
  /** True when the engine types were not available, so nothing could be checked. */
  skipped: boolean;
  problems: ValidationProblem[];
}

/**
 * Writes the files to a temp project and runs `tsc --noEmit` then eslint over them.
 *
 * The two run in that order and the first failure short-circuits: a type error usually makes
 * the linter produce a page of consequential noise, and the type error is the thing to report.
 */
export async function verifyProject(files: GeneratedFile[]): Promise<VerificationResult> {
  const engineTypes = engineTypesDir();
  if (!engineTypes) {
    return { ok: true, skipped: true, problems: [] };
  }

  const dir = mkdtempSync(path.join(tmpdir(), "specra-verify-"));
  try {
    materialise(dir, files);
    const tsconfigPath = writeTsconfig(dir, engineTypes);

    const typeErrors = typecheck(dir);
    if (typeErrors.length > 0) {
      return { ok: false, skipped: false, problems: typeErrors };
    }

    const lint = await lintProject(dir, writeLintConfig(dir, tsconfigPath));
    return { ok: lint.ok, skipped: false, problems: lint.problems };
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

/**
 * A generated project carries no tsconfig — 07-git.md's tree has none, and shipping one would
 * be Specra deciding how the user's repository compiles. So the compiler gets one here, pointed
 * at the engine types, and it is thrown away with the temp directory.
 */
function writeTsconfig(dir: string, engineTypes: string): string {
  const tsconfigPath = path.join(dir, "tsconfig.json");
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
          typeRoots: [path.join(engineTypes, "@types")],
          baseUrl: ".",
          paths: { "*": [path.join(engineTypes, "*").replace(/\\/g, "/")] },
        },
        include: ["**/*.ts"],
      },
      null,
      2,
    )}\n`,
  );
  return tsconfigPath;
}

function typecheck(dir: string): ValidationProblem[] {
  // Resolved rather than guessed: pnpm hoists typescript into a content-addressed store path.
  const tsc = createRequire(import.meta.url).resolve("typescript/bin/tsc");
  try {
    execFileSync(process.execPath, [tsc, "--noEmit", "--project", "tsconfig.json"], {
      cwd: dir,
      encoding: "utf8",
      stdio: ["ignore", "pipe", "pipe"],
    });
    return [];
  } catch (error) {
    const failure = error as { stdout?: string; stderr?: string };
    const output = `${failure.stdout ?? ""}${failure.stderr ?? ""}`.trim();
    return parseTscOutput(output, dir);
  }
}

/**
 * `path/to/file.ts(12,5): error TS2339: …` into one problem per line, so the API can show the
 * user which generated file objected rather than a wall of compiler text.
 */
function parseTscOutput(output: string, dir: string): ValidationProblem[] {
  const problems: ValidationProblem[] = [];
  for (const line of output.split(/\r?\n/)) {
    const match = /^(.+?)\((\d+),(\d+)\):\s*(error .+)$/.exec(line.trim());
    if (match) {
      problems.push({
        path: path.relative(dir, path.resolve(dir, match[1]!)).replace(/\\/g, "/"),
        message: `${match[2]}:${match[3]} ${match[4]}`,
      });
    }
  }
  // Never lose a failure to a parser: an unrecognised shape is still a refusal.
  return problems.length > 0
    ? problems
    : [{ path: "tsconfig.json", message: output || "typecheck failed with no output" }];
}

/** Null when the directory is unset or absent, which is what makes verification skippable. */
function engineTypesDir(): string | null {
  const configured = process.env[ENGINE_TYPES_ENV];
  if (!configured) {
    return null;
  }
  return existsSync(configured) ? path.resolve(configured) : null;
}
