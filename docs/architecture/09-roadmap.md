# 09 — Roadmap

Order matters more than dates. Each milestone ends with the golden path working a little
further along, on real domain logic — never on mocked behaviour behind a finished-looking UI.

## Where the repository is today

**The golden path closes.** A manual test case becomes an executable test, runs against a real
environment, comes back with evidence, and a failure produces a diagnosis and — where a diagnosis
warrants one — a repair proposal a person reviews and commits.

Working end to end: sign in, a workspace with members and roles, a project bound to a Jira/Xray
board, a manual case imported or authored, AI modelling it into an IR a person can edit, a page
inspected in a real browser so its locators are read rather than guessed, the IR projected into
Playwright source that is typechecked and linted before it is offered, a reviewer correcting that
source and applying it as a commit authored by them — optionally pushed — an environment saying
where the suite points, a run that executes in a container and brings back its trace, video and
logs behind expiring links, and a failure classified into a cause with a rationale tied to the
evidence. Underneath: Next.js + Spring Boot, i18n in Vietnamese and English, RFC 9457 errors,
correlation ids and tracing, OpenAPI, ArchUnit layering, pgvector, Spring AI with a
provider-agnostic setup and per-workspace keys.

What remains is not a missing stage but the seams between them: the loop closes in two gestures
rather than one (applying a fix commits it; the re-run is started from the Runs screen), and there
is still no live progress stream — the UI polls while a run is in flight.

Two things landed that were never on this roadmap, and both paid for themselves. **Local auth**
(V3) became necessary the moment workspaces had members; the whole API is closed by default and
every service checks a `Permission`. **Xray import** (V7–V9) was listed under "Later, and only
then" and moved up, because a real imported case is what makes modelling worth judging — a
hand-typed fixture would have told us much less.

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

## M3 — The runner ✅

- `services/runner` with the `codegen` job only, plus the Playwright adapter and golden-file
  tests.
- The generated project skeleton ([07](07-git.md)) is produced and compiles.

**Done when** a hand-written IR fixture generates a project that passes `tsc`, `eslint` and
`prettier --check`, byte-identically on every run.

**Done for `codegen`**: the package exists as `specra-runner` with the adapter
(`src/adapters/playwright/`), `generate()` as a pure `(IR, page objects, options) → files`, and
58 tests. Determinism is held by golden files over the shared `fixtures/valid/` IRs; portability
is held by a test that writes two generations into one temp project and runs the real `tsc`
against the real engine types — which is what caught a flow referencing a page object it never
constructed, and a second generation clobbering `fixtures/environment.ts`. Engine vocabulary is
contained by `containment.test.ts` with a two-entry exemption list. Generation refuses rather
than guesses: an uninspected page comes back in `unresolved`, an element named `page`/`goto`
is rejected, duplicate page names are rejected. The job server landed with M5:
`POST /jobs { kind, payload }` over HTTP. It served `codegen` alone at first; `execute` landed
with M6 and `inspect` with M8, so all three are live.

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

The codegen job now enforces the rule rather than only being able to: it typechecks and lints
its own output and answers 422 with the compiler's words instead of proposing something that
does not build. Two limits are deliberate. The engine's types arrive as a path
(`SPECRA_ENGINE_TYPES`) rather than a dependency, because depending on the engine here to check
the projection is the containment rule leaking in through the back door — unset, the runner says
so at startup instead of silently passing everything. And **a generation with unresolved targets
is not typechecked at all**: until inspection lands it references elements nobody has a locator
for, so the compiler would reject every real generation with a `TS2339` the user can do nothing
about, drowning the actionable "inspect these pages first". `verified` on the result says which
of the three happened, because a caller that cannot tell "checked and fine" from "not checked"
will eventually trust the wrong one.

Both later jobs have since landed: `execute` with M6, `inspect` with M8.

## M4 — Understanding and modelling ✅

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

## M5 — Generate, review, commit ✅

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
not been inspected.

**Done.** A reviewer edits a generated file before applying it, and the correction is what gets
committed. The edits travel on the apply request rather than mutating the stored proposal, whose
`files` column is `updatable = false` on purpose: the row is the record of what the model
produced, the commit is what the human approved, and those are different facts — an edit that
rewrote the row would leave `ai_generations` describing something nobody generated. An edit
naming a path the proposal does not contain is refused, because accepting one would quietly turn
apply into "commit any file I name", which is not what the reviewer looked at.

Push-on-apply is a switch beside the apply button, off by default: committing is local and
undoable, publishing to a remote other people pull from is a second decision.

Still open here: the SSE progress stream, which waits for M7 — a stream earns its complexity when
there is a live log to tail, and until then three seconds of polling on a job that takes minutes
is imperceptible.

## M6 — Execution ✅

- Migration V12 — `test_runs`, `test_run_items`, `test_artifacts`. (The bullet here used to say
  "V5"; V5 was spent on the model and the generated code long ago, and the numbering has moved.)
- The `execute` job, environments and secret injection, the run matrix.
- Artifacts to object storage, signed URLs, trace and video in the UI.
- Web: runs list, run detail, live status.

**Done when** a committed test runs against a real environment and the failure view shows the
trace.

Two things were already in place and only needed code. **`environments` and `environment_vars`
have existed since V2**, so environments are an entity and a screen, not a migration. And
**`automation_tests` (V5) is written for the first time here** — it had never had a row, which
looks like dead schema until you ask what it is for: `code_generations` (V11) is the history of
proposals, one row per attempt, whereas `automation_tests` is the _current_ identity of a test
inside the repository — which spec file holds it, under what title, at which commit. A run
targets "this test in that file", which no proposal row can answer. It is filled at apply, from
the SPEC file among the generated ones — a generation also writes page objects, fixtures and
config, and pointing a run at a page object would be worse than pointing it at nothing.

**Done**: the `execute` job runs the suite in a container, `RunExecutor` dispatches with the
environment's variables decrypted at the last moment and never written to the working copy, and
artifacts land in `core/storage` behind links that expire — `ArtifactRetention` deletes the
evidence once they have. Web: a runs list that re-fetches itself while anything on it is still
going, a run detail with the matrix, and the evidence per cell.

Environments are a screen on the project, beside Runs. That screen is what the milestone
actually turned on: the API had served environments since V2, but with no way to create one a
run had nowhere to point, so every execution refused with a message the user could not act on.
A secret is written once and never returned — a re-save that omits it keeps the stored value,
which is the only reason the edit form is usable at all.

Still open: the live status stream (see M5), and result sync back to Jira/Xray, which stays
under "Later".

## M7 — Analysis and repair ✅

- Migration V13 (the bullet used to say "V6"; the numbering has moved) — `failure_analyses`,
  plus `kind` and `failure_analysis_id` on `code_generations`.
- Role 5 and the evidence bundle.
- Web: root cause, proposed diff, apply, re-run.

**Done when** a locator change in the target application produces a correct one-line proposal
that a user accepts and re-runs green — the loop closes.

**Done.** `POST /run-items/{id}/analysis` classifies one failed cell into the
six causes of [08](08-ai-pipeline.md) with a confidence, a summary, a rationale tied to the
evidence, and a suggestion in words. The run detail offers it per cell.

Three decisions carry the feature. **Only FAILED is analysable** — ERROR means the run could not
complete, which is ours to fix and says nothing about the application; V12 made that a column
precisely so this could rely on it. **The reading is stored, not recomputed**: the evidence is
immutable once a run finishes, so a second press returns what is there rather than paying for a
possibly different answer to an identical question, and `reanalyse=true` is the deliberate
override. And **`PRODUCT_BUG` carries no suggestion**, stripped in the service rather than merely
discouraged in the prompt — invariant 7 is worth nothing if it depends on a model's good mood, so
a model that helpfully offers "relax the assertion" has it dropped on the way to the database.

The evidence bundle is a record, not a map, so what a model may see is auditable at a glance: the
error, its deterministic pre-classification, the failed IR step, the manual step it traces back
to, and an excerpt of the generated code. No environment values, secret or otherwise. There is no
DOM snapshot — that arrives with M8, and until then the prompt says so rather than letting a
rationale describe a page nobody looked at.

`POST /failure-analyses/{id}/repair` then turns a reading into a patch: the model is given the
committed file and the diagnosis, and returns the file with the smallest change that fixes it.
The result is a `PROPOSED` row in `code_generations` with `kind = 'FIX'` — not its own table, so
it inherits the one review-and-apply path. A second route into a user's repository is how "a
human approves every write" quietly stops being true.

The repair refuses in four cases, and the refusals are the feature: an unrepairable cause, a spec
file no longer in the repository, an unusable answer (an empty file included — committing one
would delete the test), and a model that returned the file unchanged, which the prompt asks it to
do rather than invent a change when the diagnosis does not survive contact with the code. The
`PRODUCT_BUG` check is enforced in the service, not just hidden in the UI: a UI check is a
suggestion, and this is a rule.

Still open here: **re-running from the analysis**. Applying a fix commits it, and the run has to
be started again from the Runs screen — the loop closes, but not in one gesture.

## M8 — Inspection and locator planning ✅

Deliberately after M7: analysis makes the value of good locators obvious, and inspection is
what makes them good.

- The `inspect` job, page objects, the locator planner and its scoring.
- Regeneration and impact analysis on a changed test case.

**Done: inspection and the planner.** The runner's last `not-implemented` is gone. `inspect` opens
a real page and reports how each interactive element can be addressed, scored; `feature/pageobject`
stores the best candidate as the locator and the runner-up as the fallback, and a person can
overrule either.

The split inside the runner is the point. `collect.ts` runs in the page and reports facts only —
role, accessible name, label, test id, text, a CSS path, and how many nodes each matches.
`score.ts` and `plan.ts` run in Node and decide which of those makes the best locator. The
judgement is the part worth testing, so it has to be testable without launching a browser.

Uniqueness is a gate rather than a weight: a locator matching two nodes is not a worse locator, it
is a broken one. Beyond the strategy ranking, the scorer distrusts values that look machine-written
(`css-1x7f9k`), text that carries data rather than a label (`$42.00`), and selectors that describe
where an element sits rather than what it is — because where it sits is exactly what a redesign
changes.

**This unblocks the two things that were waiting on it.** Every generation since M5 has reported
unresolved targets and has therefore never been typechecked, because there were no locators for the
compiler to check against; a project with an inspected page now gets a real page object and
verification finally runs over real output. And M4's referential validation now has page objects to
validate against.

**Done: impact analysis.** A generation carries an `impact` — which IR steps were added, removed
or modified since the version the repository holds, and which pages they touch.

The doc asks for "a minimal change proposal", and the shape that request takes here is worth
stating, because it is not the obvious one. The projection stays whole and deterministic: invariant
3 forbids a model call in the adapter, and a hand-patched projection would not be reproducible, so
regeneration cannot become a patch without giving up the property that makes it trustworthy. What
the doc is really protecting against is a reviewer facing churn they cannot interpret — so the
impact supplies the rationale instead. The file diff says what the code does; the impact says what
the _test case_ did, which is the thing the reviewer actually changed.

Matched by step id rather than position, because inserting a step at the top shifts everything
below it and a positional diff would report the whole test as rewritten — the exact noise the doc
warns about. The baseline is the last **applied** generation: a superseded or rejected proposal was
never in anyone's repository, so diffing against one would describe a change that never happened.

## Later, and only then

Dashboard beyond a simple summary · result sync back to Jira/Xray · scheduled runs · CI
triggers · parallel sharding · task-based model routing (it needs M5's cost data to judge by).

**Jira and Xray import came off this list and shipped** with M4, as V7–V9 plus
`feature/testmanagement`. Reading a real manual case — with its steps, its data column and its
ambiguities intact — is what made the modelling step judgeable; inventing fixtures would have
flattered it. Writing results _back_ is still here, because that needs runs to exist.

## Not on this roadmap

Selenium, Cypress, WebdriverIO, Appium, mobile, visual regression, performance, API testing,
enterprise RBAC, billing, marketplace. The IR exists so the first four are possible later;
building any of them now costs the golden path and buys nothing.
