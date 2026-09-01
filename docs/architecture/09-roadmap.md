# 09 — Roadmap

Order matters more than dates. Each milestone ends with the golden path working a little
further along, on real domain logic — never on mocked behaviour behind a finished-looking UI.

## Where the repository is today

The plumbing plus the first real domain: Next.js + Spring Boot, i18n in Vietnamese and
English, RFC 9457 errors, correlation ids and tracing, OpenAPI, ArchUnit layering, pgvector,
Spring AI with a provider-agnostic setup — and workspaces, projects and test cases where the
`notes` scaffold used to be. The `chat` assistant remains, now grounded in test cases through
`TestCaseContentStore`.

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

## M1 — Domain and tenancy ✅

- Migrations V2, V4 and V5 ([04](04-database.md)) — V3 became local auth, which landed
  alongside this milestone rather than on it.
- `workspace`, `project`, `testcase` features on the api side; `testcase` replaces `note`.
- Web: projects list, project shell, test case list and editor.
- **Delete `feature/note` and `features/notes`** once `testcase` covers the same ground.
  `NoteContentStore` becomes `TestCaseContentStore` so the assistant keeps working.
- **`AiAccount`: the workspace's own provider, key and budget.** `AiCredentials` already
  answers "can we call a model" for every caller, so this is a change inside that class plus a
  settings screen — bring-your-own-key needs a tenant to belong to, which is why it waits for
  this milestone and not longer. Task-based model routing follows once generations are being
  recorded (M5), because routing without the cost data to judge it is guesswork.

**Done when** a user can create a project and author a test case, in both languages, with the
error and navigation conventions the `specra-feature` skill requires.

**Done**: workspace → project → test case, end to end in both languages. References are minted
per project (`TC-1`, `TC-2`) under a row lock; editing a case whose IR exists sets the
out-of-date flag, not a status. `note` is deleted on both sides — the assistant reads test
cases through a read-only `TestCaseContentStore`, because a model writing a test case from a
chat draft is exactly what "AI proposes, a human disposes" rules out. `AiAccount` stores the
workspace's provider and budget with the key AES-GCM-encrypted at rest (refused, with the
variable to set, when `SPECRA_ENCRYPTION_KEY` is absent — never stored in plaintext), and
`AiCredentials.chatIsConfiguredFor(workspaceId)` is the seam that consults it first.

## M2 — Git ✅

- `GitProvider` port and the GitHub implementation, JGit underneath.
- Working copies, status, diff, commit, push, branches, history.
- Web: a Source Control panel.

**Done when** a project can be connected to a real repository, and a file edited in Specra
lands as a commit authored by the user.

**Done**: `core/git` is the port and `GithubGitProvider` the only class allowed to import JGit —
`ArchitectureTest` enforces that, the same containment rule the model vendors live under.
Migration V6 adds `git_credentials` (tokens encrypted by `SecretsCipher`, read back as "set")
and turns V2's `credential_id` into a real reference.

Two decisions changed on contact with the code. **One working copy per project, not per
(project, branch)**: per-branch directories exist to preserve uncommitted work across a switch,
but switching is refused while the copy is dirty, so they preserved nothing and broke a branch
created locally — the next request would look for a directory cloned from a branch nobody had
pushed. And **the copy is reached only under a per-project lock**, because a working copy is a
directory and two requests mutating one corrupt it.

A push that is not a fast-forward comes back as `GIT_PUSH_REJECTED` for the user to pull and
retry; nothing in the code path can force it. The provider is tested against a real bare
repository over `file://`, which drives the same transport code a GitHub URL does.

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
