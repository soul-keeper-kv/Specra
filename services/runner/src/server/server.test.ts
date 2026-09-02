/**
 * The job contract over a real socket, because that is what `apps/api` will actually talk to.
 *
 * A handler test would not catch a wrong status code, and the API branches on those: 200 means
 * files, 422 means the runner refused and has something for the user to read, anything else
 * means the runner itself is broken.
 */

import { tmpdir } from "node:os";
import path from "node:path";
import type { AddressInfo } from "node:net";
import type { Server } from "node:http";
import { afterAll, beforeAll, describe, expect, it } from "vitest";

import { irFixture, LOGIN_PAGES } from "../codegen/fixtures.js";
import { createRunnerServer } from "./server.js";

let server: Server;
let base: string;

beforeAll(async () => {
  server = createRunnerServer();
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  const address = server.address() as AddressInfo;
  base = `http://127.0.0.1:${address.port}`;
});

afterAll(async () => {
  await new Promise<void>((resolve, reject) =>
    server.close((error) => (error ? reject(error) : resolve())),
  );
});

async function post(body: unknown): Promise<{ status: number; body: any }> {
  const response = await fetch(`${base}/jobs`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  return { status: response.status, body: await response.json() };
}

describe("POST /jobs", () => {
  it("answers a codegen job with the generated files", async () => {
    const { status, body } = await post({
      kind: "codegen",
      payload: {
        model: irFixture("login"),
        pages: LOGIN_PAGES,
        options: { reference: "TC-104", area: "auth", adapterVersion: "0.1.0" },
      },
    });

    expect(status).toBe(200);
    expect(body.ok).toBe(true);
    expect(body.result.files.map((file: { path: string }) => file.path)).toContain(
      "tests/auth/login-with-valid-credentials.spec.ts",
    );
    expect(body.result.unresolved).toEqual([]);
  });

  /** A refusal is an answer: 422 with a message, never a 500. */
  it("reports an uninspected page as a refusal the user can act on", async () => {
    const { status, body } = await post({
      kind: "codegen",
      payload: {
        model: irFixture("login"),
        pages: LOGIN_PAGES.filter((page) => page.name !== "DashboardPage"),
        options: { reference: "TC-104", adapterVersion: "0.1.0" },
      },
    });

    // The generation still produces files; the unresolved list is what stops it upstream.
    expect(status).toBe(200);
    expect(body.result.unresolved).toEqual([
      { stepId: "s5", page: "DashboardPage", element: "heading" },
    ]);
  });

  /**
   * The interaction between the two gates, which is the easy thing to get backwards.
   *
   * A generation with unresolved targets cannot compile — the spec calls `page.element` for an
   * element nobody inspected, so the page object has no such getter. Running the compiler over
   * it would turn the actionable "inspect these pages first" into a TS2339 the user can do
   * nothing about, and until inspection lands (M8) that is *every* real generation. So
   * verification is skipped for those, and `verified` says so rather than claiming a pass.
   */
  it("does not typecheck a generation whose targets are unresolved", async () => {
    const { status, body } = await post({
      kind: "codegen",
      payload: {
        model: irFixture("login"),
        pages: LOGIN_PAGES.filter((page) => page.name !== "DashboardPage"),
        options: { reference: "TC-104", adapterVersion: "0.1.0" },
      },
    });

    expect(status).toBe(200);
    expect(body.result.unresolved.length).toBeGreaterThan(0);
    expect(body.result.verified).toBe(false);
  });

  it("refuses an invalid Test Model with the violation, not a stack trace", async () => {
    const { status, body } = await post({
      kind: "codegen",
      payload: {
        model: { ...irFixture("login"), steps: [] },
        pages: LOGIN_PAGES,
        options: { reference: "TC-1", adapterVersion: "0.1.0" },
      },
    });

    expect(status).toBe(422);
    expect(body.ok).toBe(false);
    expect(body.error.code).toBe("generation-failed");
    expect(body.error.message).toMatch(/not valid/);
  });

  it("rejects a payload that is not shaped like a codegen request", async () => {
    const { status, body } = await post({ kind: "codegen", payload: { model: {} } });

    expect(status).toBe(422);
    expect(body.error.code).toBe("malformed-payload");
  });

  it("says a job it does not know is unknown", async () => {
    const { status, body } = await post({ kind: "teleport", payload: {} });

    expect(status).toBe(400);
    expect(body.error.code).toBe("unknown-job-kind");
  });

  it("says the job it has not built yet is not built yet", async () => {
    const { status, body } = await post({ kind: "inspect", payload: {} });

    expect(status).toBe(422);
    expect(body.error.code).toBe("not-implemented");
  });

  /**
   * The runner is handed a path; it never clones, because the API holds the credentials. A
   * payload without one cannot be run and is refused before any process is spawned.
   */
  it("refuses an execute job that does not say where the working copy is", async () => {
    const { status, body } = await post({
      kind: "execute",
      payload: { baseUrl: "https://staging.acme.dev", browsers: ["chromium"] },
    });

    expect(status).toBe(422);
    expect(body.error.code).toBe("malformed-payload");
    expect(body.error.message).toMatch(/projectDir/);
  });

  it("refuses an execute job naming a browser it cannot run", async () => {
    const { status, body } = await post({
      kind: "execute",
      payload: {
        projectDir: ".",
        baseUrl: "https://staging.acme.dev",
        browsers: ["internet-explorer"],
      },
    });

    expect(status).toBe(422);
    expect(body.error.code).toBe("malformed-payload");
  });

  /**
   * A working copy that is not there is an ERROR *result*, not a refusal: the job was
   * well-formed and the runner has something to report about it. The distinction is the one
   * the API branches on to tell "your test failed" from "the runner is broken".
   */
  it("reports a missing working copy as a result rather than a refusal", async () => {
    const { status, body } = await post({
      kind: "execute",
      payload: {
        projectDir: path.join(tmpdir(), "specra-does-not-exist-", String(Date.now())),
        baseUrl: "https://staging.acme.dev",
        browsers: ["chromium"],
      },
    });

    expect(status).toBe(200);
    expect(body.ok).toBe(true);
    expect(body.result.status).toBe("ERROR");
    expect(body.result.errorMessage).toMatch(/working copy/);
    expect(body.result.items).toEqual([]);
  });

  it("has a health probe, so the API can report the runner as a state", async () => {
    const response = await fetch(`${base}/health`);
    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toMatchObject({ ok: true });
  });
});
