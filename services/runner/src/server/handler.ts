/**
 * One job in, one response out — with no HTTP anywhere in sight.
 *
 * Keeping the dispatch separate from the server means the contract can be tested without a
 * socket, and the same function serves whatever transport comes later. `server.ts` is then only
 * request parsing and status codes.
 */

import { generate } from "../codegen/generate.js";
import {
  JOB_KINDS,
  PayloadError,
  parseCodegenPayload,
  type Job,
  type JobKind,
  type JobResponse,
} from "./jobs.js";

export function isJobKind(value: unknown): value is JobKind {
  return typeof value === "string" && (JOB_KINDS as readonly string[]).includes(value);
}

export function handleJob(job: Job): JobResponse<unknown> {
  switch (job.kind) {
    case "codegen":
      return runCodegen(job.payload);
    // Both land with their milestones; answering "not implemented" beats a 404 that reads like
    // the runner is missing entirely.
    case "inspect":
    case "execute":
      return {
        ok: false,
        error: {
          code: "not-implemented",
          message: `The ${job.kind} job is not built yet.`,
        },
      };
    default: {
      const exhaustive: never = job.kind;
      return {
        ok: false,
        error: { code: "unknown-job-kind", message: `Unknown job kind ${String(exhaustive)}` },
      };
    }
  }
}

function runCodegen(payload: unknown): JobResponse<unknown> {
  try {
    return { ok: true, result: generate(parseCodegenPayload(payload)) };
  } catch (error) {
    // A refusal is a result, not a crash: an invalid IR, an uninspected page, an element whose
    // name collides. The API turns each into something the user can act on, so the message
    // travels verbatim rather than being flattened into "internal error".
    const code = error instanceof PayloadError ? error.code : "generation-failed";
    return {
      ok: false,
      error: { code, message: error instanceof Error ? error.message : String(error) },
    };
  }
}
