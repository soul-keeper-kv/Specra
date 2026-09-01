# 02 — Test Model / IR

The IR is the backbone of the product. Everything upstream of it is understanding, everything
downstream is projection.

```text
Manual test case ──► AI understanding ──► Test Model (IR) ──► adapter ──► source code
                                              │
                                              ├──► impact analysis on change
                                              ├──► failure analysis input
                                              └──► test planning, coverage, reporting
```

## The two rules that make it worth having

1. **The IR names no engine.** No `page`, `locator`, `getByRole`, `cy`, `driver`,
   `expect(...)`. If a field only makes sense to Playwright, it belongs in the adapter.
2. **The adapter is deterministic.** `(IR, page objects, options) → files` is a pure
   function. The same input produces byte-identical output. No model call inside code
   generation — the intelligence happened earlier, when the IR was built.

Together these are what stop the product collapsing into "LLM writes a spec file". They are
enforced, not hoped for: see _Validation_ below.

## Document shape

```jsonc
{
  "irVersion": 1,
  "name": "Login with valid credentials",
  "description": "A registered user signs in and lands on the dashboard.",
  "tags": ["smoke", "auth"],

  // Declared inputs. Values are supplied by an Environment at run time, never inlined.
  "parameters": [
    { "name": "username", "type": "string", "required": true },
    { "name": "password", "type": "string", "required": true, "secret": true },
  ],

  "setup": [],
  "steps": [
    {
      "id": "s1",
      "sourceStepIds": ["ts-1"], // traceability back to the manual TestStep
      "description": "Open the login page",
      "action": "navigate",
      "target": { "page": "LoginPage" },
    },
    {
      "id": "s2",
      "sourceStepIds": ["ts-2"],
      "action": "fill",
      "target": { "page": "LoginPage", "element": "usernameInput" },
      "value": { "kind": "param", "name": "username" },
    },
    {
      "id": "s3",
      "sourceStepIds": ["ts-3"],
      "action": "fill",
      "target": { "page": "LoginPage", "element": "passwordInput" },
      "value": { "kind": "secret", "name": "password" },
    },
    {
      "id": "s4",
      "sourceStepIds": ["ts-4"],
      "action": "click",
      "target": { "page": "LoginPage", "element": "submitButton" },
    },
    {
      "id": "s5",
      "sourceStepIds": ["ts-5"],
      "action": "assert",
      "target": { "page": "DashboardPage", "element": "heading" },
      "assertion": { "condition": "visible" },
    },
  ],
  "teardown": [],
}
```

## Action vocabulary

Closed set. Adding an action is a schema change with an adapter change and a test — not a
free-text string. An action the vocabulary cannot express is a signal that the manual step
was ambiguous, and the AI must say so rather than invent one.

| Group       | Actions                                                                |
| ----------- | ---------------------------------------------------------------------- |
| Navigation  | `navigate` · `reload` · `goBack` · `goForward`                         |
| Input       | `fill` · `clear` · `press` · `select` · `check` · `uncheck` · `upload` |
| Pointer     | `click` · `doubleClick` · `rightClick` · `hover` · `dragTo`            |
| Wait        | `waitFor` — a **state**, never a duration                              |
| Assertion   | `assert`                                                               |
| Composition | `useFlow` — invoke a reusable flow (login, seed data) by name          |

**There is no `sleep`.** A duration in an IR is a flake waiting to happen; the schema has no
field for one and validation rejects it.

Assertion conditions are a closed set too: `visible`, `hidden`, `enabled`, `disabled`,
`checked`, `textEquals`, `textContains`, `valueEquals`, `countEquals`, `urlMatches`,
`attributeEquals`.

## Targets

A target is a **reference**, not a selector:

```jsonc
{ "page": "LoginPage", "element": "submitButton" }
```

The element's actual locator lives on the `PageObject` entity, produced by DOM inspection and
resolved at generation time. This is deliberate and it is what makes healing cheap: when the
application renames a `data-testid`, one page object row changes and every IR that references
it regenerates correctly. Putting the selector in the IR would scatter the same fact across
every test case that touches the button.

A raw selector is permitted only as an escape hatch:

```jsonc
{ "selector": { "strategy": "css", "value": "#legacy-widget > .row:nth-child(2)" } }
```

It is recorded as technical debt on the automation test, surfaced in the UI, and never what
the AI reaches for first.

### Locator strategy, in priority order

Applied by the locator planner when a page object element is created or healed:

| Rank | Strategy | Chosen when                                         |
| ---- | -------- | --------------------------------------------------- |
| 1    | `testId` | a `data-testid` (configurable attribute) is present |
| 2    | `role`   | an ARIA role plus an accessible name is unique      |
| 3    | `label`  | a form control has an associated `<label>`          |
| 4    | `text`   | visible text is unique and not obviously volatile   |
| 5    | `css`    | a stable id or a semantic class                     |
| 6    | `xpath`  | last resort, recorded as debt                       |

The planner scores candidates on uniqueness (does it match exactly one node), stability (does
it survive re-render, does it contain generated ids or indices) and semantics (would a human
recognise it). It stores the runner-up as a fallback, which is what failure analysis proposes
first when the primary breaks.

## Values

| `kind`    | Meaning                                      | In generated code               |
| --------- | -------------------------------------------- | ------------------------------- |
| `literal` | a constant that is part of the test's intent | inlined                         |
| `param`   | supplied by the environment                  | read from config / fixture      |
| `secret`  | supplied by the environment, never logged    | read from `process.env`, masked |

**A secret is never written to the repository.** Not in a spec, not in a fixture, not in a
`.env` that gets committed. See [06 — Execution](06-execution.md) for injection.

## Versioning

- `irVersion` is an integer on every document, required.
- Schemas live as files: `test-model.v1.schema.json`, and a future `v2` alongside it. A
  version is **never edited in place** once a document with that version exists in a
  database — stored IRs must stay readable.
- A new version ships with a migrator `v1 → v2` and a fixture that round-trips through it.
- Additive optional fields do not need a new version. Anything a v1 reader would
  misunderstand does.

## Who owns the schema

| Artefact                          | Home                                       |
| --------------------------------- | ------------------------------------------ |
| `test-model.v1.schema.json`       | `packages/test-model/schema/` — the source |
| TypeScript types                  | generated from the schema, same package    |
| Java records                      | `apps/api` `core/testmodel/`, hand-written |
| Shared fixtures (valid + invalid) | `packages/test-model/fixtures/`            |

Java mirrors the schema rather than generating from it, because the records also carry JPA
and validation concerns. **A contract test on each side validates the same fixtures**, so the
two representations cannot drift silently: a fixture the schema accepts and Jackson rejects
fails the build.

`packages/test-model` may not depend on Playwright, on Next.js, or on anything in `apps/`. It
is the one package both runtimes and the browser bundle can import.

## Validation

An IR is validated before it is stored, in this order, and a failure is a rejected
generation, not a stored bad document:

1. **Schema** — JSON Schema, structural.
2. **Referential** — every `target.page`/`element` resolves against the project's page
   objects; every `value.name` is a declared parameter; every `useFlow` names a real flow.
3. **Semantic** — no engine vocabulary in any free-text field; no duration anywhere; every
   step traces back to at least one manual step (or is marked `derived`); the document ends
   in at least one `assert`.

A test case with no assertion is not a test, and the pipeline says so instead of generating a
spec that can only pass.
