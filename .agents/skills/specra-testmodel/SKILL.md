---
name: specra-testmodel
description: "Work on Specra's Test Model / IR, the Playwright adapter or the runner service — changing the IR schema, adding an action or an assertion condition, locator strategy and DOM inspection, code generation and its determinism, run jobs and artifacts. Use whenever touching packages/test-model, services/runner, core/testmodel in apps/api, or anything that turns a test case into code or code into a result."
---

# The Test Model, the adapter and the runner

This is the part of Specra that is not a CRUD app. Everything here exists to keep one
property true: **intent is a structure, and code is a projection of it.**

```text
manual test case ──► understanding ──► IR ──► adapter ──► files ──► git ──► runner ──► result
                        (AI)         (data)  (pure fn)                    (Playwright)
```

Background and the full schema rationale: [`docs/architecture/02-test-model-ir.md`](../../../docs/architecture/02-test-model-ir.md)
and [`06-execution.md`](../../../docs/architecture/06-execution.md). This skill is the
working rules.

## The three rules that must not bend

1. **The IR names no engine.** No `page`, `locator`, `getByRole`, `expect`, `cy`, `driver` —
   not in a field name, not in an enum value, not in a `description` string. If a concept
   only makes sense to Playwright, it belongs in the adapter.
2. **The adapter is a pure function.** `(IR, page objects, options) → files`, byte-identical
   every time. **No model call, no clock, no random, no filesystem read** inside it. Golden
   files are the test.
3. **No duration anywhere.** There is no `sleep`, no `timeout: 3000` in an IR. Waiting is a
   state (`waitFor` with a condition). A duration in an IR is a flake with a schema.

Violating any of these does not produce a bug today; it produces a product that cannot add a
second engine and cannot explain why a test changed. That is why they are enforced.

## Changing the schema

The schema is `packages/test-model/schema/test-model.v1.schema.json`. It has three consumers
and none of them owns it: `apps/web`, `services/runner`, and hand-written Java records in
`apps/api` `core/testmodel/`.

**Adding an optional field** — additive, no version bump:

1. Edit the schema.
2. Mirror it in `src/types.ts`. `schema-parity.test.ts` is what keeps the closed
   vocabularies honest; it fails on an action the schema has and the types do not.
3. Mirror it on the Java record and run the contract test.
4. Add a fixture that uses it.

**Anything a v1 reader would misunderstand** — a new required field, a changed meaning, a
removed value — is a **v2**:

1. `test-model.v2.schema.json` as a new file. **Never edit v1 once a v1 document exists in a
   database.** Stored IRs must stay readable.
2. A `v1 → v2` migrator with a round-trip fixture.
3. Readers accept both; writers emit the newest.

**Adding an action or an assertion condition** — the vocabulary is closed on purpose. All of:

1. the schema enum,
2. the adapter case, with a golden file,
3. the understanding prompt's action list,
4. both message bundles if it is ever shown to a user.

An action added to the schema but not the adapter is a generation that fails at codegen; the
contract test should catch it before that.

If the AI wants an action the vocabulary has no room for, the correct output is
`TEST_CASE_AMBIGUOUS` with a question — not a new free-text action.

## Targets and locators

A target is a **reference**: `{ "page": "LoginPage", "element": "submitButton" }`. The
locator lives on the `PageObject` entity and is resolved at generation time.

- **Never put a selector in an IR step** except through the explicit escape hatch, which is
  recorded as debt on the automation test and surfaced in the UI.
- Locator priority is `testId → role → label → text → css → xpath`, scored on uniqueness,
  stability and semantics. Store the runner-up as the fallback — that is what a failure
  analysis proposes first.
- **Never let a model pick a locator when a DOM snapshot exists.** Inspect, score, choose. A
  guessed `#login-btn` is the single largest source of flake in tools of this kind.

## Working in `services/runner`

```text
services/runner/src/
├── adapters/playwright/   the ONLY place @playwright/test may be imported for codegen
├── codegen/               scaffolding, assembly, prettier + eslint + tsc validation
├── inspect/               DOM → structured page representation
├── execute/               run a project, collect artifacts, parse results
└── server/                the job API apps/api calls
```

- `adapters/playwright/` is a containment boundary, the same idea as "no LLM vendor named in
  Java". An eslint `no-restricted-imports` rule and a unit test enforce it. Engine vocabulary
  outside that folder is the bug.
- **A generation that does not compile is never proposed.** Codegen runs prettier, eslint and
  `tsc --noEmit` over its own output; a failure makes the `AiGeneration` `FAILED`, not
  `PROPOSED`. If valid IR produced uncompilable code, that is an adapter bug — add the golden
  file, do not retry the model.
- The runner is **stateless**: no database, nothing on disk that survives a job. Everything
  arrives in the job payload and leaves in the result or in object storage.
- `services/runner` drives the **user's** application. `tests/e2e` drives **Specra**. They
  both use Playwright and they share no code — never import between them.

## Generated project shape

```text
tests/  pages/  fixtures/  flows/  utils/  playwright.config.ts  package.json
.specra/project.json          adapter version and conventions
.specra/models/TC-104.json    the IR, committed beside the code it produced
```

Page Object Model, because a locator change should be one edit. `.specra/` is committed so
the repository is self-describing, and nothing at test time depends on it.

The acceptance test for the whole output: **clone the repo, `pnpm install`,
`npx playwright test` — it runs with no reference to Specra.**

## Secrets

- `value.kind: "secret"` is a **name**, never a value. The value never enters an IR, a
  prompt, a generated file, a commit, or a log.
- Generated code reads `process.env`; the runner receives decrypted values at dispatch and
  holds them in memory only.
- A run whose IR declares a parameter no environment supplies fails with
  `ENVIRONMENT_NOT_CONFIGURED` before a browser starts.

## Validation order

Schema → referential → semantic. A failure is a rejected generation, never a stored bad
document.

| Layer       | Catches                                                                       |
| ----------- | ----------------------------------------------------------------------------- |
| Schema      | structure, enums, required fields                                             |
| Referential | unknown page/element, undeclared parameter, unknown flow                      |
| Semantic    | engine vocabulary, a duration, a step tracing to nothing, no assertion at all |

A test case whose IR contains no `assert` is not a test. Say so; do not generate a spec that
can only pass.

## Tests to write, every time

| Change                | Test                                                          |
| --------------------- | ------------------------------------------------------------- |
| Schema field          | a valid fixture and an invalid one, checked on both sides     |
| Action or condition   | a golden file for the generated output                        |
| Adapter change at all | the existing golden files still match, byte for byte          |
| Locator scoring       | a DOM fixture with a known best and a known fallback          |
| Runner job            | a job payload in, a result out, with no network to a real app |
