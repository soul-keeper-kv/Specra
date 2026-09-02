/**
 * The HTTP surface: `POST /jobs`, plus a health probe.
 *
 * Node's own `http` module rather than a framework — the surface is two routes, and a dependency
 * here would be one more thing shipping into the container that runs model-written code.
 */

import {
  createServer,
  type IncomingMessage,
  type Server,
  type ServerResponse,
} from "node:http";

import { handleJob, isJobKind } from "./handler.js";

/** A job payload carries an IR and its page objects; large, but not unbounded. */
const MAX_BODY_BYTES = 4 * 1024 * 1024;

export function createRunnerServer(): Server {
  return createServer((request, response) => {
    void route(request, response);
  });
}

async function route(request: IncomingMessage, response: ServerResponse): Promise<void> {
  if (request.method === "GET" && request.url === "/health") {
    send(response, 200, { ok: true, result: { status: "up" } });
    return;
  }

  if (request.method !== "POST" || request.url !== "/jobs") {
    send(response, 404, {
      ok: false,
      error: { code: "unknown-job-kind", message: "POST /jobs is the only endpoint." },
    });
    return;
  }

  let body: string;
  try {
    body = await read(request);
  } catch (error) {
    send(response, 413, {
      ok: false,
      error: {
        code: "malformed-payload",
        message: error instanceof Error ? error.message : "The request body could not be read.",
      },
    });
    return;
  }

  let job: unknown;
  try {
    job = JSON.parse(body);
  } catch {
    send(response, 400, {
      ok: false,
      error: { code: "malformed-payload", message: "The request body is not valid JSON." },
    });
    return;
  }

  const kind = (job as { kind?: unknown } | null)?.kind;
  if (!isJobKind(kind)) {
    send(response, 400, {
      ok: false,
      error: { code: "unknown-job-kind", message: `Unknown job kind ${String(kind)}` },
    });
    return;
  }

  const result = await handleJob({ kind, payload: (job as { payload?: unknown }).payload });
  // A refused job is a complete answer, not a server fault: 422, so the API can tell "the runner
  // said no" apart from "the runner fell over".
  send(response, result.ok ? 200 : 422, result);
}

function read(request: IncomingMessage): Promise<string> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    let size = 0;
    request.on("data", (chunk: Buffer) => {
      size += chunk.length;
      if (size > MAX_BODY_BYTES) {
        reject(new Error(`The request body exceeds ${MAX_BODY_BYTES} bytes.`));
        request.destroy();
        return;
      }
      chunks.push(chunk);
    });
    request.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    request.on("error", reject);
  });
}

function send(response: ServerResponse, status: number, body: unknown): void {
  const text = JSON.stringify(body);
  response.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(text),
  });
  response.end(text);
}
