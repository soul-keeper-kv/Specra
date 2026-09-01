# 01 — Domain model

## The shape

```text
Workspace                       tenant boundary — every query is scoped by it
 ├── WorkspaceMember            user × workspace × role
 ├── AiAccount                  the workspace's own provider, key and budget (BYOK)
 └── Project                    one automation project = one Git repository
      ├── GitRepository         provider, remote, default branch, credential ref
      ├── Environment           baseUrl + variables + secrets (DEV / STAGING / PROD)
      ├── TestCase              the manual case, authored or imported
      │    ├── TestStep         ordered, human language
      │    ├── TestModel        the IR — versioned, one current, history kept
      │    └── AutomationTest   the generated artefact's identity in the repo
      ├── PageObject            a known page/screen and its inspected elements
      ├── TestRun               one execution request
      │    └── TestResult       per test × browser × environment
      │         └── TestArtifact  screenshot, video, trace, log, DOM snapshot
      ├── AiGeneration          one AI proposal: IR, code, or fix — with a diff
      └── Integration           Jira / Xray / TestRail — later, behind one port
```

## Why these boundaries

- **Project ≡ repository.** One project maps to exactly one Git repository and one default
  branch. Two repositories is two projects. This keeps "where does this code go" answerable
  without a resolution step.
- **TestCase and AutomationTest are different things.** The test case is intent and can
  outlive five rewrites of the code. The automation test is a path in a repository plus the
  identity of the test inside that file. Deleting generated code must not delete intent.
- **TestModel is versioned, not overwritten.** Regeneration and impact analysis both need
  the previous IR to diff against ([08](08-ai-pipeline.md)). One row per generation, with a
  pointer on the test case to the current one.
- **PageObject is a first-class entity, not a side effect of codegen.** DOM inspection
  produces it; several test cases share it; locator healing updates it in one place instead
  of in every spec. This is what makes fixes small.
- **TestRun is the request; TestResult is the outcome per matrix cell.** One run over three
  browsers and one environment is one `TestRun` and three `TestResult`s.
- **AiAccount hangs off the workspace, not the project.** A key and a budget belong to
  whoever pays, and that is the account. It holds the provider, the encrypted key, an optional
  model preference and a spending ceiling; a workspace without one falls back to the
  platform's, and a workspace with neither is _unconfigured_ — a state the product reports and
  offers to fix, never a failure. `AiCredentials` in `apps/api` is already the single place
  that answers "can we call a model", so this lands there and nowhere else.
- **AiGeneration is an audit record and a proposal, in one row.** Every model call that
  could change the repository is recorded before anything is applied, with the prompt
  fingerprint, the model, the token cost, the produced artefact, and the user's decision.

## State machines

Statuses are enums in code and check-constrained in the database. Nothing else is a status.

**TestCase.automationStatus** — how far the case has got down the pipeline:

```text
NOT_AUTOMATED ──► MODELLED ──► GENERATED ──► COMMITTED
      ▲                                          │
      └──────────────── (code deleted) ──────────┘
```

`OUT_OF_DATE` is a flag, not a state: it is set when the test case is edited after its IR
was generated, and cleared by regeneration.

**TestRun.status:**

```text
QUEUED ──► RUNNING ──┬──► PASSED
                     ├──► FAILED     the test ran and an assertion or locator failed
                     ├──► ERROR      the run could not complete (build, timeout, infra)
                     └──► CANCELLED
```

`FAILED` and `ERROR` are deliberately distinct: only `FAILED` is worth an AI failure
analysis, `ERROR` is usually ours to fix.

**AiGeneration.status:**

```text
PENDING ──► PROPOSED ──┬──► APPLIED    a person accepted it; a commit exists
                       ├──► REJECTED
                       └──► SUPERSEDED
        └──► FAILED    the model or the validation step refused it
```

**A generation never goes from `PROPOSED` to a file on disk without a user action.** That
transition is the human-in-the-loop principle, expressed as a state machine.

## Identity and tenancy

- Primary keys are `uuid`, generated in the application, as `notes` already does.
- Every table below `Workspace` carries `workspace_id`, even where it is reachable through a
  parent. Tenancy filtering that depends on a join is tenancy filtering that gets forgotten.
- Human-facing identifiers (`TC-104`, `RUN-88`) are per-project sequences, separate from the
  uuid, because users read them out loud and paste them into Jira.

## Mapping onto the existing code

The current `feature/note` and its `NoteContentStore` are scaffold that proved the
`core/content` port. `TestCase` replaces `Note` as the first real feature and reuses the
pattern: a `TestCaseContentStore` is what lets the assistant read test cases without knowing
where they live. See [09 — Roadmap](09-roadmap.md) for when `note` is removed.
