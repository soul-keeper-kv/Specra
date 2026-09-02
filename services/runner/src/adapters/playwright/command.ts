import { createRequire } from "node:module";
import path from "node:path";

/**
 * The engine's command line, built here so nothing above the adapter types it.
 *
 * `execute/run.ts` knows it is spawning "the engine" with "these specs on these browsers"; which
 * binary that is, and which flags say those things, is this file's business. A second engine
 * writes a second one of these.
 */

export interface EngineCommandRequest {
  /** Repo-relative spec paths. Empty runs the whole suite. */
  specs: string[];
  browsers: string[];
  /** Where the reporter and the artifacts are written. */
  outputDir: string;
}

/**
 * The engine's CLI as a JavaScript file, resolved from the project being run.
 *
 * Not `npx`, and not the `playwright` launcher: both are shell scripts on Windows, which
 * `spawn` can only start with `shell: true` — and a shell concatenates arguments instead of
 * escaping them, in the one component that executes code a model wrote against a repository we
 * cloned. Running the CLI's own entry point with the current Node binary keeps `shell: false`
 * on every platform.
 *
 * Resolving it from `projectDir` also means the run uses the engine version that project pinned,
 * which is what makes a run against a recorded commit sha reproducible.
 */
export function engineCliPath(projectDir: string): string {
  const require_ = createRequire(path.join(projectDir, "package.json"));
  return require_.resolve("@playwright/test/cli");
}

/**
 * The arguments that say "run these specs on these browsers, and tell me what happened".
 *
 * Each browser is a `--project`, which is exactly how the generated config declares them, so
 * the matrix the API asked for is the matrix the engine runs.
 */
export function engineCommand(request: EngineCommandRequest): string[] {
  const args = ["test", "--reporter=json", `--output=${request.outputDir}`];

  // The trace is forced on, and only the trace: it is the artifact failure analysis reads in
  // M7, and the generated config keeps it at `on-first-retry` because tracing every local run
  // is slow for the user. Asking for it here gets one without editing their repository.
  //
  // Video and screenshot are *not* set, because `--trace` is the only one of the three the CLI
  // accepts — checked against `playwright test --help`, not assumed. They do not need to be:
  // the generated config already asks for both on failure, which is what a run wants anyway.
  args.push("--trace", "retain-on-failure");

  for (const browser of request.browsers) {
    args.push("--project", browser);
  }
  // Paths last: everything after them would be read as another path.
  args.push(...request.specs);

  return args;
}
