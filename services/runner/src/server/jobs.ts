/**
 * The job contract `apps/api` calls (06-execution.md).
 *
 * `POST /jobs { kind, payload }` → `{ ok, result }` or `{ ok: false, error }`. One shape for all
 * three kinds, because the control plane should not learn a new envelope per job.
 *
 * Stateless: nothing here reads a database or keeps anything between calls. Everything the job
 * needs arrives in the payload, and everything it produces leaves in the result.
 */

import type { CodegenRequest, CodegenResult } from "../codegen/types.js";
import { BROWSERS, type ExecuteRequest, type ExecuteResult } from "../execute/types.js";

export const JOB_KINDS = ["codegen", "inspect", "execute"] as const;

export type JobKind = (typeof JOB_KINDS)[number];

export interface Job {
  kind: JobKind;
  payload: unknown;
}

/** Machine-readable, because the API branches on it and translates for the user. */
export type JobErrorCode =
  "unknown-job-kind" | "malformed-payload" | "not-implemented" | "generation-failed";

export type JobResponse<T> =
  { ok: true; result: T } | { ok: false; error: { code: JobErrorCode; message: string } };

export type CodegenJobResult = CodegenResult;

export type ExecuteJobResult = ExecuteResult;

/**
 * A payload is JSON from another runtime, so it is validated rather than cast. The IR itself is
 * checked against the shared schema inside `generate()`; this only proves the envelope is the
 * shape the adapter can be handed.
 */
export function parseCodegenPayload(payload: unknown): CodegenRequest {
  if (typeof payload !== "object" || payload === null) {
    throw new PayloadError("the payload must be an object");
  }
  const candidate = payload as Partial<CodegenRequest>;
  if (typeof candidate.model !== "object" || candidate.model === null) {
    throw new PayloadError("model is required and must be the Test Model document");
  }
  if (!Array.isArray(candidate.pages)) {
    throw new PayloadError("pages is required and must be an array of page objects");
  }
  if (candidate.flows !== undefined && !Array.isArray(candidate.flows)) {
    throw new PayloadError("flows must be an array when present");
  }
  const options = candidate.options;
  if (typeof options !== "object" || options === null) {
    throw new PayloadError("options is required");
  }
  if (typeof options.reference !== "string" || options.reference.trim() === "") {
    throw new PayloadError("options.reference is required");
  }
  if (typeof options.adapterVersion !== "string" || options.adapterVersion.trim() === "") {
    throw new PayloadError("options.adapterVersion is required");
  }
  return candidate as CodegenRequest;
}

/**
 * The execute payload, checked the same way — it arrives as JSON from another runtime.
 *
 * `variables` is deliberately not logged or echoed anywhere: it carries decrypted secrets for
 * the length of one job, and the result never contains it.
 */
export function parseExecutePayload(payload: unknown): ExecuteRequest {
  if (typeof payload !== "object" || payload === null) {
    throw new PayloadError("the payload must be an object");
  }
  const candidate = payload as Partial<ExecuteRequest>;

  if (typeof candidate.projectDir !== "string" || candidate.projectDir.trim() === "") {
    throw new PayloadError(
      "projectDir is required: the runner does not clone, it is given a path",
    );
  }
  if (typeof candidate.baseUrl !== "string" || candidate.baseUrl.trim() === "") {
    throw new PayloadError("baseUrl is required; it becomes BASE_URL for the suite");
  }
  if (!Array.isArray(candidate.browsers) || candidate.browsers.length === 0) {
    throw new PayloadError("browsers is required and must name at least one browser");
  }
  for (const browser of candidate.browsers) {
    if (!(BROWSERS as readonly string[]).includes(browser)) {
      throw new PayloadError(`unknown browser ${String(browser)}`);
    }
  }
  if (candidate.specs !== undefined && !Array.isArray(candidate.specs)) {
    throw new PayloadError("specs must be an array of repo-relative paths when present");
  }

  return candidate as ExecuteRequest;
}

export class PayloadError extends Error {
  readonly code: JobErrorCode = "malformed-payload";
}
