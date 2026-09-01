# 09 — Roadmap

Order matters more than dates. Each milestone ends with the golden path working a little
further along, on real domain logic — never on mocked behaviour behind a finished-looking UI.

## Where the repository is today

A working scaffold, not the product: Next.js + Spring Boot, i18n in Vietnamese and English,
RFC 9457 errors, correlation ids and tracing, OpenAPI, ArchUnit layering, pgvector, Spring AI
with a provider-agnostic setup, and a `notes` + `chat` demo that exercises all of it.

The plumbing is the part worth keeping. `note` and `chat` are placeholders.

## M0 — The contract ✅

Nothing else can be built well before this exists.

- `packages/test-model`: `test-model.v1.schema.json` (draft 2020-12), a TypeScript mirror of
  its types, an ajv validator, and the fixtures — `valid/`, `invalid/schema/` and
  `invalid/semantic/`.
- `core/testmodel` in `apps/api`: the Java records, and `TestModelSchema`, which validates
  against **the same schema file**, copied onto the classpath by the build rather than
  reimplemented.
- Contract and parity tests on both sides, over the same fixtures.
- ArchUnit rule: no execution engine named in Java.

**Done**: 31 tests in `packages/test-model`, 34 in `TestModelContractTest` and
`TestModelVocabularyTest`, all without Docker. The schema makes two things unrepresentable
rather than merely illegal — a duration, and a step that traces to nothing.

The semantic layer (undeclared parameters, a secret read as a plain parameter, duplicate step
ids, a test that asserts nothing) is deliberately **not** here. It runs where an IR is
authored and stored, in `apps/api`, and lands with M4. `fixtures/invalid/semantic/` already
holds the cases, schema-valid on purpose, so the layer has its target before it is written.

## M1 — Domain and tenancy

- Migrations V2–V4 ([04](04-database.md)).
- `workspace`, `project`, `testcase` features on the api side; `testcase` replaces `note`.
- Web: projects list, project shell, test case list and editor.
- **Delete `feature/note` and `features/notes`** once `testcase` covers the same ground.
  `NoteContentStore` becomes `TestCaseContentStore` so the assistant keeps working.

**Done when** a user can create a project and author a test case, in both languages, with the
error and navigation conventions the `specra-feature` skill requires.

## M2 — Git

- `GitProvider` port and the GitHub implementation, JGit underneath.
- Working copies, status, diff, commit, push, branches, history.
- Web: a Source Control panel.

**Done when** a project can be connected to a real repository, and a file edited in Specra
lands as a commit authored by the user.

## M3 — The runner

- `services/runner` with the `codegen` job only, plus the Playwright adapter and golden-file
  tests.
- The generated project skeleton ([07](07-git.md)) is produced and compiles.

**Done when** a hand-written IR fixture generates a project that passes `tsc`, `eslint` and
`prettier --check`, byte-identically on every run.

## M4 — Understanding and modelling

- Roles 1 and 2 ([08](08-ai-pipeline.md)) behind `POST /test-cases/{id}/model`.
- IR validation with useful rejections; ambiguity surfaced, not guessed.
- Web: the IR viewer and editor, and the two-panel test case screen.

**Done when** a real manual test case produces a valid IR, and a deliberately ambiguous one
produces a question instead of a fabrication.

## M5 — Generate, review, commit

- `POST /test-cases/{id}/code`, the proposal/diff/apply flow, `ai_generations` audit.
- Web: the code editor with diff, accept, edit, and commit.

**Done when** a manual test case becomes a reviewed, committed Playwright spec without anyone
writing code — the first half of the golden path, end to end.

## M6 — Execution

- Migration V5, the `execute` job, environments and secret injection, the run matrix.
- Artifacts to object storage, signed URLs, trace and video in the UI.
- Web: runs list, run detail, live status.

**Done when** a committed test runs against a real environment and the failure view shows the
trace.

## M7 — Analysis and repair

- Migration V6 for `FIX` generations, role 5, the evidence bundle.
- Web: root cause, proposed diff, apply, re-run.

**Done when** a locator change in the target application produces a correct one-line proposal
that a user accepts and re-runs green — the loop closes.

## M8 — Inspection and locator planning

Deliberately after M7: analysis makes the value of good locators obvious, and inspection is
what makes them good.

- The `inspect` job, page objects, the locator planner and its scoring.
- Regeneration and impact analysis on a changed test case.

## Later, and only then

Dashboard beyond a simple summary · Jira and Xray import · result sync back · scheduled runs ·
CI triggers · parallel sharding.

## Not on this roadmap

Selenium, Cypress, WebdriverIO, Appium, mobile, visual regression, performance, API testing,
enterprise RBAC, billing, marketplace. The IR exists so the first four are possible later;
building any of them now costs the golden path and buys nothing.
