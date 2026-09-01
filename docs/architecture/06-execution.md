# 06 — Execution

## The runner

`services/runner` is a stateless Node service. It owns no database and no long-lived state:
everything it needs arrives in the job, and everything it produces goes back in the result or
to object storage.

```text
apps/api                      services/runner                   object storage
   │                                │                                 │
   │ POST /jobs  { kind, payload }  │                                 │
   ├───────────────────────────────►│                                 │
   │                                │  materialise working copy       │
   │                                │  pnpm install (cached)          │
   │                                │  npx playwright test …          │
   │                                │  collect trace/video/shots      │
   │                                ├────────────────────────────────►│
   │◄───────────────────────────────┤  result + artifact keys         │
   │  progress events (SSE)         │                                 │
```

Three job kinds, one service, because all three need the same toolchain:

| Kind      | Input                                 | Output                                   |
| --------- | ------------------------------------- | ---------------------------------------- |
| `codegen` | IR + page objects + project options   | files, plus the result of typecheck/lint |
| `inspect` | url + environment                     | a structured page representation         |
| `execute` | working copy ref + selection + matrix | per-test results, failed step, artifacts |

## Generation is validated before it is proposed

The adapter is deterministic and the compiler is free, so use it:

```text
IR ──► adapter ──► files ──► prettier ──► eslint ──► tsc --noEmit ──► proposal
                                │
                                └── any failure → the generation is FAILED, not PROPOSED
```

A generation that does not compile never reaches the user as a proposal. If the adapter can
produce uncompilable output from a valid IR, that is an adapter bug and it gets a golden-file
test, not a retry loop around the model.

## Run lifecycle

```text
QUEUED ──► RUNNING ──┬──► PASSED
                     ├──► FAILED     assertion or locator failed — worth AI analysis
                     ├──► ERROR      install, build, timeout, infra — ours to fix
                     └──► CANCELLED
```

A run is created against a **commit sha**, not against "the current working copy". Two people
looking at the same run see the same code. A run of uncommitted edits is allowed but the sha
is recorded as the base and the diff is stored with the run, so the result is still
reproducible.

The matrix is `tests × browsers`, over one environment. One `test_run`, one `test_run_item`
per cell.

## Artifacts

Playwright already produces exactly what failure analysis needs; the job is to keep it, not
to reinvent it.

| Artifact                | Retention                     | Why it matters                                  |
| ----------------------- | ----------------------------- | ----------------------------------------------- |
| Trace                   | on first retry and on failure | the single most useful debugging artefact       |
| Video                   | on failure                    | what a non-technical QA will actually watch     |
| Screenshot              | on failure                    | the thumbnail in the run list                   |
| Console + network log   | on failure                    | root cause is often here, not in the DOM        |
| DOM snapshot at failure | on failure                    | what the locator planner needs to propose a fix |

Artifacts are written to object storage under `runs/{runId}/{itemId}/…`, referenced by key,
and served to the browser through short-lived signed URLs. The API never proxies bytes and
Postgres never stores them.

Retention has a default expiry, because traces are large and a QA team runs a lot of tests.

## Configuration and secrets

Generated code reads configuration; it never contains it.

```text
Environment (DB, secrets encrypted)
        │  injected at dispatch, in memory only
        ▼
   runner process env
        │
        ▼
playwright.config.ts → baseURL, fixtures → credentials
```

- `baseUrl` comes from the environment, so the same spec runs against DEV and STAGING.
- Secrets are decrypted at dispatch, passed to the runner job, never written to the working
  copy, never logged, and masked in captured output.
- A run whose IR declares a parameter the environment does not supply fails fast with
  `ENVIRONMENT_NOT_CONFIGURED` — before a browser starts, not as a mysterious empty field.

## Isolation

MVP runs jobs in a container per run with no credentials beyond the job's own. This is not
optional politeness: the runner executes code that a model wrote against a repository we
cloned. Treat it as untrusted from day one, because retrofitting a sandbox around a service
that assumed trust is a rewrite.

Concurrency is a queue with a per-workspace limit. A workspace cannot starve another.

## Why not run Playwright from Java

It was considered. It requires shelling out to Node anyway, gives up the ability to typecheck
generated code with the real toolchain, and puts browser processes inside the process that
holds the database connections and the tenant data. The boundary in
[03](03-module-boundaries.md) exists to avoid all three.
