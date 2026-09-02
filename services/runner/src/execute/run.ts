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

import {
  engineCliPath,
  engineCommand,
  engineEntrypoint,
  engineImage,
  engineReportEnv,
  readReport,
} from "../adapters/playwright/index.js";
import {
  CONTAINER_OUTPUT_DIR,
  CONTAINER_OUTPUT_MOUNT,
  containerCommand,
  isolationAvailable,
  type Isolation,
} from "./sandbox.js";
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

    // Chosen before anything else runs, because it decides *where* the rest happens — and a
    // failure after this point must still report which path it was on.
    const isolation = await chooseIsolation(request);

    // Both paths install, because the image supplies the *browsers* (at PLAYWRIGHT_BROWSERS_PATH)
    // and not the engine's npm package — verified by inspecting the image rather than assumed.
    // The container mounts the result read-only, so what it executes is a copy it cannot alter.
    //
    // This is the sandbox's remaining soft edge, and it is worth naming: `npm install` runs the
    // repository's lifecycle scripts on the host. Moving it inside the container needs a
    // writable overlay of the working copy, which is the next step rather than this one.
    const install = await installDependencies(request.projectDir);
    if (install) {
      return errored(outputDir, started, install, isolation);
    }

    const reportFile = path.join(outputDir, "report.json");
    const run = await runSuite(request, outputDir, reportFile, isolation);

    if (!existsSync(reportFile)) {
      // No report at all means the engine never got as far as running: a config error, a
      // missing browser, a crash. That is ERROR — ours to fix — not a failing test.
      return errored(
        outputDir,
        started,
        run.output.trim() || "The test run produced no report.",
        isolation,
      );
    }

    const items = readReport(JSON.parse(readFileSync(reportFile, "utf8")), outputDir);
    return {
      status: statusOf(items),
      items,
      outputDir,
      isolation,
      durationMs: Date.now() - started,
    };
  } catch (error) {
    // `isolation` is not in scope here — an exception can predate the choice — so this is the
    // one place it is not reported. The message carries the stack instead: an earlier version
    // returned a bare "spawn EINVAL" that named neither the command nor the path it was on,
    // and finding out which took a dozen probes.
    const detail = error instanceof Error ? (error.stack ?? error.message) : String(error);
    return errored(outputDir, started, detail);
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
  // A report with no results is not a pass. The engine can write an empty report when it found
  // no spec matching the selection, or exited before running anything — and "nothing ran" and
  // "everything passed" must never look the same, for exactly the reason an unreported matrix
  // cell becomes ERROR rather than PASSED.
  if (items.length === 0) return "ERROR";
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
    process.execPath,
    [npmCliPath(), lockfile ? "ci" : "install"],
    projectDir,
    {},
    INSTALL_TIMEOUT_MS,
  );
  return result.code === 0
    ? null
    : `Installing dependencies failed:\n${result.output.trim().slice(-2000)}`;
}

/**
 * Runs the suite, in a container when one can be had.
 *
 * The two paths differ only in where the process lives: the engine arguments, the environment
 * and the report file are the same either way, so a sandboxed run and a direct one produce
 * identical results and the sandbox cannot quietly change behaviour.
 */
async function runSuite(
  request: ExecuteRequest,
  outputDir: string,
  reportFile: string,
  isolation: Isolation,
): Promise<{ code: number | null; output: string }> {
  const timeoutMs = request.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  const environment = {
    // The generated config reads BASE_URL, so the same suite points anywhere.
    BASE_URL: request.baseUrl,
    // Decrypted by the API at dispatch. Passed to the child as arguments or environment and
    // never written to the working copy, so a secret does not reach the user's repository.
    ...(request.variables ?? {}),
  };

  if (isolation === "container") {
    const args = engineCommand({
      specs: request.specs ?? [],
      browsers: request.browsers,
      // Inside the container the output is at a fixed path; the mount makes it the same
      // directory the API will read from afterwards.
      outputDir: CONTAINER_OUTPUT_DIR,
    });

    const { command, args: dockerArgs } = containerCommand({
      projectDir: request.projectDir,
      outputDir,
      variables: {
        ...environment,
        // At the mount root, not in CONTAINER_OUTPUT_DIR: the host reads the report from the
        // top of `outputDir`, and the engine wipes its artifacts directory before each run —
        // which would take the report with it.
        ...engineReportEnv(`${CONTAINER_OUTPUT_MOUNT}/${path.basename(reportFile)}`),
      },
      engineArgs: args,
      image: engineImage(),
      entrypoint: engineEntrypoint(),
    });
    // MSYS_NO_PATHCONV stops Git Bash rewriting the container's own paths. `/work` becomes
    // `C:/Program Files/Git/work` on the way through, and docker rejects it — a failure that
    // looks like a bad flag and is really the shell editing an argument in transit. Harmless
    // everywhere else, so it is set unconditionally rather than guessed from the platform.
    return run(command, dockerArgs, request.projectDir, { MSYS_NO_PATHCONV: "1" }, timeoutMs);
  }

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
    { ...environment, ...engineReportEnv(reportFile) },
    timeoutMs,
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
    const child = spawn(command, args, {
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
 * npm's own JavaScript entry point, beside the Node binary that is running.
 *
 * Not the `npm` launcher: it is a `.cmd` shim on Windows, and Node refuses to `spawn` one
 * without a shell — the same `EINVAL` the engine hit, for the same reason. Running the CLI
 * under `process.execPath` keeps `shell: false` everywhere, which is the property that matters
 * in the component that executes code a model wrote.
 */
function npmCliPath(): string {
  return path.join(path.dirname(process.execPath), "node_modules", "npm", "bin", "npm-cli.js");
}

/**
 * Container when one is available and not switched off, the direct path otherwise.
 *
 * `SPECRA_RUNNER_ISOLATION=process` forces the direct path — for a machine with no daemon, and
 * for the runner's own tests, which must not need one. Anything else prefers the container and
 * falls back rather than failing, because a development machine losing Docker should degrade
 * to "less isolated" rather than to "cannot run tests at all". The choice is reported either
 * way, so an operator never has to guess which they got.
 */
async function chooseIsolation(request: ExecuteRequest): Promise<Isolation> {
  if (request.isolation === "process" || process.env.SPECRA_RUNNER_ISOLATION === "process") {
    return "process";
  }
  return (await isolationAvailable()) ? "container" : "process";
}

function errored(
  outputDir: string,
  started: number,
  message: string,
  isolation: Isolation = "process",
): ExecuteResult {
  return {
    status: "ERROR",
    items: [],
    outputDir,
    isolation,
    errorMessage: message,
    durationMs: Date.now() - started,
  };
}

/** Drops the artifact directory once the API has read what it needs from it. */
export function discard(outputDir: string): void {
  rmSync(outputDir, { recursive: true, force: true });
}
