/**
 * Installing a working copy's dependencies — the step both `execute` and `inspect` need.
 *
 * It lives apart from either because both need it for the same reason and must not drift: a
 * repository that can be inspected is one that can be run, and two copies of this logic would
 * eventually disagree about which is which.
 *
 * The runner never clones and never holds a credential. `apps/api` materialises the working copy
 * and passes a path; this only fills in `node_modules` beneath it.
 */

import { spawn } from "node:child_process";
import { existsSync } from "node:fs";
import path from "node:path";

/** Installing dependencies is slower than any test and must not share a run's budget. */
export const INSTALL_TIMEOUT_MS = 10 * 60 * 1000;

/**
 * How much of npm's output to keep when it fails.
 *
 * From the *front*. npm prints the reason first and then, on a usage error, its entire help
 * text — so keeping the tail is keeping the one part that says nothing. An out-of-sync lockfile
 * reached the user as a list of `--allow-file` flags for exactly this reason.
 */
const ERROR_EXCERPT = 2000;

/**
 * `npm ci` when the lockfile agrees with the manifest, `npm install` otherwise, skipped when
 * `node_modules` is already there.
 *
 * npm rather than pnpm: this is the *user's* repository, and it may be checked out anywhere by
 * anyone. npm is the one package manager a Node install always has.
 *
 * Returns `null` on success, or a message for the user.
 */
export async function installDependencies(projectDir: string): Promise<string | null> {
  if (existsSync(path.join(projectDir, "node_modules"))) {
    return null;
  }
  // No manifest, no install. npm without a package.json walks *up* the directory tree looking
  // for one, so a repository that has none reaches whatever project happens to sit above the
  // working copy and tries to resolve its tree instead — which is both wrong and, against a
  // pnpm tree, a crash inside npm. Say what is missing rather than let npm guess.
  if (!existsSync(path.join(projectDir, "package.json"))) {
    return (
      "This repository has no package.json, so there are no dependencies to install and " +
      "no engine to run. Generate and commit a test project first."
    );
  }

  const result = await npm(installArgs(projectDir), projectDir);

  // `npm ci` refuses outright when the lockfile does not match the manifest — a committed
  // lockfile that was never filled in is the common case, and it is not something a QA user can
  // diagnose from npm's own words. `install` is the documented fix, so take it rather than
  // report a failure the user would resolve by running the very command we could have run.
  if (result.code !== 0 && isLockfileMismatch(result.output)) {
    const retry = await npm(["install"], projectDir);
    return retry.code === 0 ? null : failure(retry.output);
  }

  return result.code === 0 ? null : failure(result.output);
}

/** `ci` only with a lockfile: it is the reproducible install, and it requires one to exist. */
function installArgs(projectDir: string): string[] {
  return existsSync(path.join(projectDir, "package-lock.json")) ? ["ci"] : ["install"];
}

/** npm's own words for it, matched on the sentence rather than the exit code, which is generic. */
function isLockfileMismatch(output: string): boolean {
  return /can only install packages when your package\.json and package-lock\.json/i.test(
    output,
  );
}

function failure(output: string): string {
  return `Installing dependencies failed:\n${excerpt(output)}`;
}

/** The head of npm's output, where it says what went wrong. */
export function excerpt(output: string): string {
  const trimmed = output.trim();
  return trimmed.length <= ERROR_EXCERPT
    ? trimmed
    : `${trimmed.slice(0, ERROR_EXCERPT)}\n… output truncated.`;
}

/**
 * npm's own JavaScript entry point, beside the Node binary that is running.
 *
 * Not the `npm` launcher: it is a `.cmd` shim on Windows, and Node refuses to `spawn` one
 * without a shell — the same `EINVAL` the engine hit, for the same reason. Running the CLI
 * under `process.execPath` keeps `shell: false` everywhere, which is the property that matters
 * in the component that executes code a model wrote.
 */
export function npmCliPath(): string {
  return path.join(path.dirname(process.execPath), "node_modules", "npm", "bin", "npm-cli.js");
}

function npm(args: string[], cwd: string): Promise<{ code: number | null; output: string }> {
  return new Promise((resolve) => {
    const child = spawn(process.execPath, [npmCliPath(), ...args], {
      cwd,
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

    const timer = setTimeout(() => {
      child.kill("SIGKILL");
      output += `\nInstalling exceeded its ${Math.round(INSTALL_TIMEOUT_MS / 1000)}s budget.`;
    }, INSTALL_TIMEOUT_MS);

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
