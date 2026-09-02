/**
 * One job in, one response out — with no HTTP anywhere in sight.
 *
 * Keeping the dispatch separate from the server means the contract can be tested without a
 * socket, and the same function serves whatever transport comes later. `server.ts` is then only
 * request parsing and status codes.
 */

import { generate } from "../codegen/generate.js";
import { formatFiles } from "../codegen/validate.js";
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

export async function handleJob(job: Job): Promise<JobResponse<unknown>> {
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

/**
 * Generate, then format.
 *
 * Prettier is the authority on how the output is written, and it is async, so it runs here
 * rather than inside `generate()` — which stays a pure, synchronous projection with golden
 * files over it. The user receives what prettier would have written, which is what makes
 * "the generated project passes `prettier --check`" true by construction instead of by a
 * template author remembering where the line breaks go.
 */
async function runCodegen(payload: unknown): Promise<JobResponse<unknown>> {
  try {
    const result = generate(parseCodegenPayload(payload));
    return { ok: true, result: { ...result, files: await formatFiles(result.files) } };
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
