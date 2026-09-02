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

**Done for `codegen`**: the package exists as `specra-runner` with the adapter
(`src/adapters/playwright/`), `generate()` as a pure `(IR, page objects, options) → files`, and
53 tests. Determinism is held by golden files over the shared `fixtures/valid/` IRs; portability
is held by a test that writes two generations into one temp project and runs the real `tsc`
against the real engine types — which is what caught a flow referencing a page object it never
constructed, and a second generation clobbering `fixtures/environment.ts`. Engine vocabulary is
contained by `containment.test.ts` with a two-entry exemption list. Generation refuses rather
than guesses: an uninspected page comes back in `unresolved`, an element named `page`/`goto`
is rejected, duplicate page names are rejected. The job server landed with M5:
`POST /jobs { kind, payload }` over HTTP, serving `codegen` while `inspect`/`execute` answer
`not-implemented`.

All three tools now run over the output. **Prettier is the authority on the bytes, not a check
on them**: it formats in the job layer (`runCodegen`) rather than inside `generate()`, because
prettier 3 is async-only and awaiting inside the one function the product's determinism rests
on would ripple `await` through every call site to change nothing about the result. So the
golden files record post-prettier bytes — what actually lands in the user's repository — and
`checkFormatting` then asserts the adapter and prettier agree, which turns a drifted template
into a failing test instead of a reformatted file in someone's first commit. `eslint` runs
typed, over a materialised project, with the rules that catch a template bug (unused imports,
floating promises) and none about style, which is prettier's. `require-await` is off with a
reason: an uninspected page's `goto()` throws rather than guessing a URL, and it stays `async`
because inspection later fills in a real navigation.

Still open here: the `inspect` and `execute` jobs, which land with M8 and M6.

## M4 — Understanding and modelling

- Roles 1 and 2 ([08](08-ai-pipeline.md)) behind `POST /test-cases/{id}/model`.
- IR validation with useful rejections; ambiguity surfaced, not guessed.
- Web: the IR viewer and editor, and the two-panel test case screen.

**Done when** a real manual test case produces a valid IR, and a deliberately ambiguous one
produces a question instead of a fabrication.

**In flight** (ahead of M3, because the Xray path made the modelling step the next thing a user
sees): `feature/testmodel` with `POST /test-cases/{id}/model` answering synchronously, the
semantic layer (`TestModelSemantics`, caught against `fixtures/invalid/semantic/`), one repair
round, `test_models` versions, and the `ai_generations` audit (V10). Refusals are
`test-case-ambiguous` with the questions and `test-model-invalid` with the violations, both as
problem extensions. The web side is the automation workspace reached from an Xray test: manual
steps on the left, the model step beside the manual step it came from in the middle, pages
awaiting inspection on the right.

`PUT …/model` now lets a person correct the IR directly, which is what keeps invariant 4 from
being decorative: a reviewer shown a wrong step and offered only "generate again" is a
spectator, not the author. The edit is stored as version n+1 rather than overwriting what it
corrected, because regeneration and impact analysis both diff against what came before. It
validates through `TestModelValidation` in `core/testmodel` — the schema, binding and semantic
chain extracted from `TestModellingService` so both callers use one gate. That extraction is
the point of the change: a hand-written path with its own slightly different validation is
exactly how "the stored IR is always valid" quietly stops being true, and the human path must
not be the lenient one.

Still open here: referential validation against page objects, which waits for M8 to have any.

## M5 — Generate, review, commit

- `POST /test-cases/{id}/code`, the proposal/diff/apply flow, `ai_generations` audit.
- Web: the code editor with diff, accept, edit, and commit.

**Done when** a manual test case becomes a reviewed, committed Playwright spec without anyone
writing code — the first half of the golden path, end to end.

**In flight**: the chain is connected end to end. `apps/api` reaches the runner through
`core/runner` — `RunnerClient` is the port and `HttpRunnerClient` the only class that knows it
is HTTP, the same containment shape as `GitProvider`. `feature/codegen` stores each proposal in
`code_generations` (V11) with its files and its unresolved targets, and
`POST /code-generations/{id}/apply` is the only path that writes into a working copy: it writes
the proposed files, commits exactly those paths as the signed-in user, and moves the case to
`COMMITTED`. Generate and apply are separate endpoints on purpose — there is no call that does
both. Web: the workspace's Test script tab is the review surface, with a file list, a line
diff, apply and reject, and the warning the runner's `unresolved` list produces when a page has
not been inspected. Still open here: editing a generated file before applying, push-on-apply
from the UI, and the SSE progress stream.

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
