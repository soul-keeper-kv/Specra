/**
 * One job in, one response out — with no HTTP anywhere in sight.
 *
 * Keeping the dispatch separate from the server means the contract can be tested without a
 * socket, and the same function serves whatever transport comes later. `server.ts` is then only
 * request parsing and status codes.
 */

import { generate } from "../codegen/generate.js";
import { formatFiles } from "../codegen/validate.js";
import { verifyProject } from "../codegen/verify.js";
import { execute } from "../execute/run.js";
import {
  JOB_KINDS,
  PayloadError,
  parseCodegenPayload,
  parseExecutePayload,
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
    case "execute":
      return runExecute(job.payload);
    // Lands with M8; answering "not implemented" beats a 404 that reads like the runner is
    // missing entirely.
    case "inspect":
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
 * Generate, format, then verify.
 *
 * Prettier is the authority on how the output is written, and it is async, so it runs here
 * rather than inside `generate()` — which stays a pure, synchronous projection with golden
 * files over it. The user receives what prettier would have written, which is what makes
 * "the generated project passes `prettier --check`" true by construction instead of by a
 * template author remembering where the line breaks go.
 *
 * Verification then decides whether this is a proposal at all. `06-execution.md`: a generation
 * that does not compile never reaches the user as a proposal. It is a refusal rather than a
 * crash — 422 with the compiler's own words — because if valid IR produced uncompilable code
 * that is an adapter bug for us to fix with a golden file, and the user needs to be told it
 * failed rather than shown a spec that will not run.
 *
 * `verified` on the result says which of the three happened: the tools ran and passed, or they
 * were skipped because the engine's types are not installed, or skipped because the generation
 * has unresolved targets and is not expected to compile yet. A caller that cannot tell "checked
 * and fine" from "not checked" will eventually trust the wrong one.
 */
/**
 * Runs a suite and answers with results and evidence.
 *
 * A run that finished with failing tests is a **success** at this layer — `ok: true` with a
 * FAILED status — because the job did what it was asked. Only a malformed payload is a refusal.
 * Getting that backwards would turn "your test found a bug" into "the runner is broken", and
 * the API would report the wrong thing to the user.
 */
async function runExecute(payload: unknown): Promise<JobResponse<unknown>> {
  try {
    return { ok: true, result: await execute(parseExecutePayload(payload)) };
  } catch (error) {
    const code = error instanceof PayloadError ? error.code : "generation-failed";
    return {
      ok: false,
      error: { code, message: error instanceof Error ? error.message : String(error) },
    };
  }
}

async function runCodegen(payload: unknown): Promise<JobResponse<unknown>> {
  try {
    const result = generate(parseCodegenPayload(payload));
    const files = await formatFiles(result.files);

    // A generation with unresolved targets is *known* not to compile: the spec references
    // `page.element` for an element nobody has inspected, so the page object has no such getter.
    // That is the designed outcome until inspection lands (M8) — `unresolved` is the actionable
    // warning "inspect these pages first", and running the compiler over it would turn every
    // proposal into a TS2339 the user can do nothing with. Verify only what can be judged.
    const verification =
      result.unresolved.length > 0
        ? { ok: true, skipped: true, problems: [] }
        : await verifyProject(files);
    if (!verification.ok) {
      return {
        ok: false,
        error: {
          code: "generation-failed",
          message: [
            "The generated project does not compile, so it was not proposed:",
            ...verification.problems.map((problem) => `  ${problem.path} ${problem.message}`),
          ].join("\n"),
        },
      };
    }

    return { ok: true, result: { ...result, files, verified: !verification.skipped } };
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
