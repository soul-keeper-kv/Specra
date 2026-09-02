/**
 * The `execute` job's contract: what `apps/api` sends, and what comes back.
 *
 * Engine-free in the same way the codegen contract is. A caller says "run these tests on these
 * browsers against this URL"; nothing here mentions the tool that does it, and the shape would
 * not change if a second engine were added — only `../adapters/playwright/` would.
 */

/** Which engine names a browser this way is the adapter's business; these are the three we run. */
export const BROWSERS = ["chromium", "firefox", "webkit"] as const;

export type Browser = (typeof BROWSERS)[number];

export interface ExecuteRequest {
  /**
   * Where the working copy already is on disk.
   *
   * The runner does not clone: `apps/api` holds the credentials and the audit trail, so it
   * materialises the copy at the commit the run names and hands over a path. That keeps the one
   * component executing untrusted code from also being the one holding tokens.
   */
  projectDir: string;
  /** Spec files to run, repo-relative. Empty means the whole suite. */
  specs?: string[];
  browsers: Browser[];
  /** `BASE_URL` for the generated config; the same suite points anywhere. */
  baseUrl: string;
  /**
   * Injected into the child process only. Decrypted by the API at dispatch, never written to the
   * working copy, and masked out of anything this job returns.
   */
  variables?: Record<string, string>;
  /** Whole-run ceiling in milliseconds; a hung suite must not hold a worker for ever. */
  timeoutMs?: number;
}

export type ItemStatus = "PASSED" | "FAILED" | "ERROR" | "SKIPPED";

/** One matrix cell: one spec on one browser. */
export interface ExecutedItem {
  /** Repo-relative spec path, so the API can match it back to an automation test. */
  specPath: string;
  /** The `test(...)` title, which is the other half of a test's identity in a repository. */
  title: string;
  browser: Browser;
  status: ItemStatus;
  durationMs: number;
  /**
   * The IR step the failure landed on, when the generated code said so.
   *
   * A step id rather than a line number: that is what lets the UI highlight the manual step the
   * user wrote and the generated line at once, and it survives the file being regenerated.
   */
  failedStepId?: string;
  errorMessage?: string;
  errorType?: string;
  /** Paths, relative to the run's output directory, of what the engine kept. */
  artifacts: ExecutedArtifact[];
}

export type ArtifactKind = "SCREENSHOT" | "VIDEO" | "TRACE" | "LOG" | "DOM";

export interface ExecutedArtifact {
  kind: ArtifactKind;
  /** Relative to `outputDir`; the API uploads it and stores only the storage key. */
  path: string;
  contentType?: string;
  sizeBytes?: number;
}

export interface ExecuteResult {
  /**
   * PASSED only when every cell passed. FAILED when a test failed on its merits; ERROR when the
   * run could not complete — install, build, timeout, a missing browser. The distinction is what
   * decides whether a failure is worth an AI analysis or is ours to fix.
   */
  status: "PASSED" | "FAILED" | "ERROR";
  items: ExecutedItem[];
  /** Where artifacts were written, so the API can read them before the directory is dropped. */
  outputDir: string;
  /** Set when `status` is ERROR: what stopped the run, in words a user can act on. */
  errorMessage?: string;
  /** How long the whole job took, including install. */
  durationMs: number;
}
