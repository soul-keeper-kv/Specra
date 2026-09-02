/**
 * The `execute` job: run a generated project and bring back results and evidence.
 *
 * Three steps, and the boundaries between them are where the design lives.
 *
 * **Install** is `npm ci`/`npm install` in the project directory, skipped when `node_modules`
 * is already there. A generated project pins its engine version, so the install is
 * reproducible; it is the slow part, and caching it is what makes a second run quick.
 *
 * **Run** is the engine's CLI with a JSON reporter writing to a *file*. Not stdout: a suite
 * prints, and generated suites will, so sharing that channel with the results would corrupt
 * them the first time a test logged something.
 *
 * **Read** turns the report into `ExecutedItem`s through the adapter, which is the only place
 * that knows the report's shape.
 *
 * The runner never clones and never holds a credential. `apps/api` materialises the working
 * copy at the commit the run names and passes a path, so the component executing untrusted
 * code is not also the one holding tokens.
 */

import { spawn } from "node:child_process";
import { existsSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

import { engineCliPath, engineCommand, readReport } from "../adapters/playwright/index.js";
import type { ExecuteRequest, ExecuteResult, ExecutedItem } from "./types.js";

/** A suite that has not finished by now is not going to; the default is generous on purpose. */
const DEFAULT_TIMEOUT_MS = 15 * 60 * 1000;

/** Installing dependencies is slower than any test and must not share the run's budget. */
const INSTALL_TIMEOUT_MS = 10 * 60 * 1000;

export async function execute(request: ExecuteRequest): Promise<ExecuteResult> {
  const started = Date.now();
  const outputDir = mkdtempSync(path.join(tmpdir(), "specra-run-"));

  try {
    if (!existsSync(request.projectDir)) {
      return errored(
        outputDir,
        started,
        `The working copy ${request.projectDir} is not there.`,
      );
    }

    const install = await installDependencies(request.projectDir);
    if (install) {
      return errored(outputDir, started, install);
    }

    const reportFile = path.join(outputDir, "report.json");
    const run = await runSuite(request, outputDir, reportFile);

    if (!existsSync(reportFile)) {
      // No report at all means the engine never got as far as running: a config error, a
      // missing browser, a crash. That is ERROR — ours to fix — not a failing test.
      return errored(
        outputDir,
        started,
        run.output.trim() || "The test run produced no report.",
      );
    }

    const items = readReport(JSON.parse(readFileSync(reportFile, "utf8")), outputDir);
    return {
      status: statusOf(items),
      items,
      outputDir,
      durationMs: Date.now() - started,
    };
  } catch (error) {
    return errored(outputDir, started, error instanceof Error ? error.message : String(error));
  }
}

/**
 * PASSED only when every cell passed.
 *
 * FAILED and ERROR are kept apart all the way up because only FAILED is worth an AI analysis:
 * a failed assertion says something about the application, a timeout or a crashed worker says
 * something about us. A run with both is ERROR — the failures cannot be trusted to mean
 * anything while something else was broken.
 */
function statusOf(items: ExecutedItem[]): ExecuteResult["status"] {
  if (items.some((item) => item.status === "ERROR")) return "ERROR";
  if (items.some((item) => item.status === "FAILED")) return "FAILED";
  return "PASSED";
}

/**
 * `npm ci` when there is a lockfile, `npm install` otherwise, skipped when already installed.
 *
 * npm rather than pnpm: this is the *user's* repository, and it may be checked out anywhere by
 * anyone. npm is the one package manager a Node install always has.
 */
async function installDependencies(projectDir: string): Promise<string | null> {
  if (existsSync(path.join(projectDir, "node_modules"))) {
    return null;
  }
  const lockfile = existsSync(path.join(projectDir, "package-lock.json"));
  const result = await run(
    "npm",
    [lockfile ? "ci" : "install"],
    projectDir,
    {},
    INSTALL_TIMEOUT_MS,
  );
  return result.code === 0
    ? null
    : `Installing dependencies failed:\n${result.output.trim().slice(-2000)}`;
}

async function runSuite(
  request: ExecuteRequest,
  outputDir: string,
  reportFile: string,
): Promise<{ code: number | null; output: string }> {
  const args = engineCommand({
    specs: request.specs ?? [],
    browsers: request.browsers,
    outputDir,
  });

  // The engine CLI as a JS file run by this Node binary: no shell, on any platform.
  return run(
    process.execPath,
    [engineCliPath(request.projectDir), ...args],
    request.projectDir,
    {
      // The generated config reads BASE_URL, so the same suite points anywhere.
      BASE_URL: request.baseUrl,
      // Decrypted by the API at dispatch. In this process only: never written to the working
      // copy, and the child inherits them without them touching disk.
      ...(request.variables ?? {}),
      PLAYWRIGHT_JSON_OUTPUT_NAME: reportFile,
    },
    request.timeoutMs ?? DEFAULT_TIMEOUT_MS,
  );
}

/**
 * One child process, its output captured, killed if it outstays the budget.
 *
 * **Never `shell: true`.** Node warns about it for good reason: with a shell, arguments are
 * concatenated rather than escaped, so a spec path or a browser name out of a repository we
 * cloned could become a shell fragment — and this is the one component that runs code a model
 * wrote.
 *
 * The engine sidesteps the problem entirely by being started as a JS file under this Node
 * binary. Only `npm` still needs {@link executable}, because installing has no equivalent
 * entry point to call directly.
 */
function run(
  command: string,
  args: string[],
  cwd: string,
  env: Record<string, string>,
  timeoutMs: number,
): Promise<{ code: number | null; output: string }> {
  return new Promise((resolve) => {
    const child = spawn(executable(command), args, {
      cwd,
      env: { ...process.env, ...env, CI: "1" },
      shell: false,
      stdio: ["ignore", "pipe", "pipe"],
    });

    let output = "";
    const capture = (chunk: Buffer) => {
      output += chunk.toString();
    };
    child.stdout.on("data", capture);
    child.stderr.on("data", capture);

    const timer = setTimeout(() => {
      child.kill("SIGKILL");
      output += `\nThe run exceeded its ${Math.round(timeoutMs / 1000)}s budget and was stopped.`;
    }, timeoutMs);

    child.on("error", (error) => {
      clearTimeout(timer);
      resolve({ code: -1, output: `${output}\n${error.message}` });
    });
    child.on("close", (code) => {
      clearTimeout(timer);
      resolve({ code, output });
    });
  });
}

/**
 * `npm` is a shell script on Windows, where `spawn` without a shell can only exec a real
 * executable — hence `.cmd`. An absolute path (the Node binary) is passed through untouched.
 */
function executable(command: string): string {
  return process.platform === "win32" && !path.isAbsolute(command) ? `${command}.cmd` : command;
}

function errored(outputDir: string, started: number, message: string): ExecuteResult {
  return {
    status: "ERROR",
    items: [],
    outputDir,
    errorMessage: message,
    durationMs: Date.now() - started,
  };
}

/** Drops the artifact directory once the API has read what it needs from it. */
export function discard(outputDir: string): void {
  rmSync(outputDir, { recursive: true, force: true });
}
