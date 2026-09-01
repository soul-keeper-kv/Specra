# 03 — Module boundaries

## The decision: two runtimes, one boundary

The product blueprint sketches a Node monorepo. This repository already has a Spring Boot
API with the tenancy, persistence, i18n, error and tracing plumbing built. Playwright, on the
other hand, only exists in Node, and generated TypeScript can only be typechecked by the real
TypeScript toolchain.

So the split is drawn where the tooling forces it, and nowhere else:

```text
                     ┌──────────────────────────────┐
                     │  apps/web — Next.js (:3000)  │
                     └──────────────┬───────────────┘
                                    │ REST + SSE
                     ┌──────────────▼───────────────┐
                     │  apps/api — Spring Boot      │   the control plane
                     │  :8080                       │
                     │                              │
                     │  tenancy · projects          │
                     │  test cases · IR of record   │
                     │  AI orchestration (Spring AI)│
                     │  Git operations (JGit)       │
                     │  run orchestration · results │
                     └──────────────┬───────────────┘
                                    │ HTTP, job-shaped
                     ┌──────────────▼───────────────┐
                     │  services/runner — Node/TS   │   the toolchain plane
                     │                              │
                     │  Playwright adapter (codegen)│
                     │  generated-code validation   │
                     │  DOM inspection              │
                     │  test execution + artifacts  │
                     └──────────────────────────────┘

     packages/test-model   ← the IR contract; imported by web and runner, mirrored in Java
```

**The rule for which side a job goes to: does it need the Node/Playwright toolchain?** If
yes, `services/runner`. If no, `apps/api`. Nothing else — not "it feels more like
TypeScript", not "it would be easier in Java".

## What lives where, and why

| Concern                            | Home                  | Because                                                            |
| ---------------------------------- | --------------------- | ------------------------------------------------------------------ |
| Workspace, project, test case CRUD | `apps/api`            | tenancy, JPA, RFC 9457 errors and i18n already live there          |
| The IR of record                   | `apps/api`            | it is persisted, versioned and audited like any other entity       |
| The IR **contract**                | `packages/test-model` | three consumers, none of them may own it                           |
| Prompting, model calls, RAG        | `apps/api`            | Spring AI, provider abstraction and chat memory are already set up |
| IR → source code                   | `services/runner`     | the output must be typechecked, linted and formatted for real      |
| Generated-code validation          | `services/runner`     | `tsc --noEmit`, eslint, prettier — Node tools                      |
| DOM inspection                     | `services/runner`     | it drives a real browser                                           |
| Test execution                     | `services/runner`     | Playwright is Node-only                                            |
| Git clone / commit / push          | `apps/api`            | it holds the credentials and the audit trail                       |
| Artifacts (trace, video, shots)    | object storage        | written by the runner, referenced by the API, never in Postgres    |

Two consequences worth naming:

- **The runner is stateless and owns no database.** It receives everything it needs in the
  job and returns a result. That is what lets it scale out and be sandboxed later.
- **The API never spawns a browser and never shells out to `npx`.** If a Java class wants to
  do either, the work is on the wrong side of the boundary.

## Import direction

```text
apps/web ──────► packages/test-model          (types for the editor and the run views)
services/runner ► packages/test-model
apps/api ───────► (mirrors the schema; contract-tested against the same fixtures)

packages/test-model ──► nothing in this repo
services/runner ──────► nothing in apps/
apps/web ─────────────► nothing in services/
tests/e2e ────────────► apps/web/src/messages only, through @messages/*
```

`packages/test-model` importing Playwright, or `apps/web` importing the runner, is the same
class of mistake as `core` importing a feature: it collapses a boundary that exists so a
second implementation is possible later.

## Inside `apps/api` — the existing shape, unchanged

`web/ → service/ → domain/`, `core` underneath, one folder per feature. The new features are
just features:

```text
dev.specra.api/
├── core/
│   ├── error/ i18n/ logging/ web/ content/     (already there)
│   └── testmodel/      the IR records + schema contract test
└── feature/
    ├── workspace/      tenancy, membership
    ├── project/        project, environments
    ├── testcase/       manual cases and steps  ← replaces `note`
    ├── testmodel/      IR generation, versions, validation
    ├── automation/     generated code identity, page objects
    ├── git/            GitProvider port + GitHub adapter, working copies
    ├── run/            run orchestration, results, artifacts
    └── ai/             understanding, generation, analysis  (already there)
```

Two ports, and only two, both earning their place the way `ContentStore` does:

| Port           | Lives in       | Implementations                                      |
| -------------- | -------------- | ---------------------------------------------------- |
| `ContentStore` | `core/content` | test cases, and whatever the assistant reads next    |
| `GitProvider`  | `feature/git`  | GitHub now; GitLab and Bitbucket are the whole point |

`ExecutionEngine` is **not** a Java port. The engine boundary is the IR plus the adapter, and
both sit in the runner. Adding Selenium later means a second adapter in the runner, not a
second Java implementation. Do not invent a Java interface with one implementation to
"prepare" for it.

## Inside `services/runner`

```text
services/runner/src/
├── adapters/
│   └── playwright/     IR → TypeScript. The ONLY engine-specific folder in the repo.
├── codegen/            project scaffolding, file assembly, formatting, validation
├── inspect/            DOM extraction → structured page representation
├── execute/            run a Playwright project, collect artifacts, parse results
└── server/             the job API the control plane calls
```

`adapters/playwright/` is the containment boundary: nothing outside it may import
`@playwright/test`, and an eslint `no-restricted-imports` rule plus a unit test enforce that,
mirroring the ArchUnit vendor rule on the Java side.

## Enforcement

Every boundary above has something that fails a build when it is crossed:

| Boundary                                       | Enforced by                                                  |
| ---------------------------------------------- | ------------------------------------------------------------ |
| api layering, `core` ignorance, feature cycles | `ArchitectureTest` (ArchUnit)                                |
| no LLM vendor named in Java                    | `ArchitectureTest`                                           |
| no execution engine named outside the adapter  | `ArchitectureTest` (Java) + eslint restricted imports (Node) |
| IR schema ↔ Java records                       | shared fixtures, validated on both sides                     |
| adapter determinism                            | golden-file tests: same IR in, identical bytes out           |
| `packages/test-model` stays app-free           | its only runtime dependency is `ajv`                         |

A rule with no test is a paragraph nobody reads. When adding a boundary, add its check in the
same change.

## Not microservices

This is a modular monolith plus one worker, and that is the intended end state for a long
while. The runner is a separate process because it needs a different runtime and different
isolation, not because we are decomposing. Do not split `apps/api` further without a reason
that survives a page of writing.
