/**
 * Playwright's JSON report → the engine-free `ExecutedItem` shape.
 *
 * This lives in the adapter for the same reason the code templates do: the report's structure is
 * the engine's, and everything above `execute/` should not learn it. A second engine writes a
 * second reader here and nothing else changes.
 *
 * The report is read from a file rather than stdout on purpose — a suite that prints to stdout
 * (and generated suites will) would otherwise corrupt the only channel carrying the results.
 */

import path from "node:path";

import type {
  ArtifactKind,
  Browser,
  ExecutedArtifact,
  ExecutedItem,
  ItemStatus,
} from "../../execute/types.js";

/** The fragment of Playwright's report shape this reads. Deliberately partial and defensive. */
interface RawReport {
  suites?: RawSuite[];
  errors?: { message?: string }[];
}

interface RawSuite {
  title?: string;
  file?: string;
  suites?: RawSuite[];
  specs?: RawSpec[];
}

interface RawSpec {
  title?: string;
  file?: string;
  tests?: RawTest[];
}

interface RawTest {
  projectName?: string;
  results?: RawResult[];
}

interface RawResult {
  status?: string;
  duration?: number;
  error?: { message?: string; stack?: string };
  errors?: { message?: string }[];
  attachments?: { name?: string; path?: string; contentType?: string }[];
  steps?: RawStep[];
}

interface RawStep {
  title?: string;
  error?: { message?: string };
  steps?: RawStep[];
}

/**
 * Every leaf spec of the report, flattened into one row per (spec × browser).
 *
 * Only the last attempt of a test is reported: a test that passed on retry passed, and a run
 * detail that showed both would be describing the retry mechanism rather than the outcome.
 */
export function readReport(report: unknown, outputDir: string): ExecutedItem[] {
  const parsed = report as RawReport;
  const items: ExecutedItem[] = [];
  for (const suite of parsed.suites ?? []) {
    collect(suite, suite.file ?? "", outputDir, items);
  }
  return items;
}

function collect(suite: RawSuite, file: string, outputDir: string, into: ExecutedItem[]): void {
  const specFile = suite.file ?? file;

  for (const spec of suite.specs ?? []) {
    for (const test of spec.tests ?? []) {
      const last = (test.results ?? []).at(-1);
      if (!last) continue;
      into.push(toItem(spec, test, last, spec.file ?? specFile, outputDir));
    }
  }

  for (const child of suite.suites ?? []) {
    collect(child, specFile, outputDir, into);
  }
}

function toItem(
  spec: RawSpec,
  test: RawTest,
  result: RawResult,
  specFile: string,
  outputDir: string,
): ExecutedItem {
  const message = result.error?.message ?? result.errors?.[0]?.message;
  return {
    specPath: normalise(specFile),
    title: spec.title ?? "",
    browser: (test.projectName ?? "chromium") as Browser,
    status: toStatus(result.status),
    durationMs: Math.round(result.duration ?? 0),
    failedStepId: failedStepId(result.steps),
    errorMessage: message ? strip(message) : undefined,
    errorType: message ? classify(message) : undefined,
    artifacts: attachmentsOf(result, outputDir),
  };
}

/**
 * Playwright's own vocabulary, mapped onto ours.
 *
 * `timedOut` and `interrupted` become ERROR rather than FAILED deliberately: neither says the
 * application under test is wrong, and only FAILED is worth asking a model to analyse.
 */
function toStatus(status: string | undefined): ItemStatus {
  switch (status) {
    case "passed":
      return "PASSED";
    case "failed":
      return "FAILED";
    case "skipped":
      return "SKIPPED";
    default:
      return "ERROR";
  }
}

/**
 * The IR step id of the step that failed, from the report's step tree.
 *
 * The adapter wraps each IR step in `test.step("… [s3]", …)`, and the report records which of
 * those carries the error. Read from the tree rather than by matching the id in the error text:
 * the top-level message is the assertion's own words and does not mention the step at all —
 * verified against a real report rather than assumed.
 *
 * The deepest failing step wins, because Playwright marks every ancestor of a failure as failed
 * too and the innermost one is the actual site. Absent — a failure in a fixture, or before any
 * step began — the run still records the error, just with no step to highlight.
 */
function failedStepId(steps: RawStep[] | undefined): string | undefined {
  for (const step of steps ?? []) {
    if (!step.error) continue;
    const deeper = failedStepId(step.steps);
    if (deeper) return deeper;
    const match = /\[(s\d+)\]\s*$/.exec(step.title ?? "");
    if (match) return match[1];
  }
  return undefined;
}

/**
 * A coarse cause, for the run list and for deciding what to analyse.
 *
 * Deliberately shallow: a real diagnosis is M7's job with the trace in hand. This only has to
 * separate "the locator was not there" from "the assertion did not hold" well enough to sort by.
 */
function classify(message: string): string {
  const text = message.toLowerCase();
  if (text.includes("timeout") && text.includes("locator")) return "LOCATOR_TIMEOUT";
  if (text.includes("resolved to") && text.includes("elements")) return "LOCATOR_AMBIGUOUS";
  if (text.includes("strict mode violation")) return "LOCATOR_AMBIGUOUS";
  if (text.includes("timeout")) return "TIMEOUT";
  if (text.includes("expect(")) return "ASSERTION";
  if (text.includes("net::") || text.includes("econnrefused")) return "NETWORK";
  return "OTHER";
}

/** Playwright colours its messages; the API stores text, not terminal escapes. */
function strip(message: string): string {
  // eslint-disable-next-line no-control-regex
  return message.replace(/\[[0-9;]*m/g, "").trim();
}

function attachmentsOf(result: RawResult, outputDir: string): ExecutedArtifact[] {
  const artifacts: ExecutedArtifact[] = [];
  for (const attachment of result.attachments ?? []) {
    if (!attachment.path) continue;
    const kind = kindOf(attachment.name, attachment.contentType);
    if (!kind) continue;
    artifacts.push({
      kind,
      path: normalise(path.relative(outputDir, attachment.path)),
      contentType: attachment.contentType,
    });
  }
  return artifacts;
}

function kindOf(
  name: string | undefined,
  contentType: string | undefined,
): ArtifactKind | null {
  if (name === "trace") return "TRACE";
  if (name === "video") return "VIDEO";
  if (name === "screenshot") return "SCREENSHOT";
  if (contentType?.startsWith("image/")) return "SCREENSHOT";
  if (contentType?.startsWith("video/")) return "VIDEO";
  return null;
}

/** Windows writes backslashes; a storage key and a repo path are always forward slashes. */
function normalise(filePath: string): string {
  return filePath.replace(/\\/g, "/");
}
