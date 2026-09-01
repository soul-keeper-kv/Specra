# 00 — Product

## What Specra is

An **AI automation layer** that sits between test management and the automation stack:

```text
Requirement / test management        (Jira, Xray, TestRail, Qase, or a hand-written case)
              │
              ▼
        ┌───────────┐
        │  Specra   │
        └───────────┘
              │
              ▼
            Git                      (the user's repository — their code, their history)
              │
              ▼
        Playwright                   (execution)
              │
              ▼
         Test result
```

Specra does not replace anything in that picture. It replaces the **human hours** spent
translating a written test case into maintainable automation code, and the human hours spent
diagnosing why that automation broke.

## Who it is for

| User                 | Has                            | Lacks                                                 |
| -------------------- | ------------------------------ | ----------------------------------------------------- |
| Manual QA / QC       | test cases, business knowledge | Playwright, automation architecture, appetite to code |
| Automation QA / SDET | both                           | time for boilerplate, patience for locator churn      |
| Developer            | both, plus the repo            | context on what QA meant by step 3                    |

The manual QA is the primary user; the SDET is the reason the output has to be **real,
reviewable source code** rather than a proprietary script format.

## The golden path

Every milestone is judged by whether this still works end to end:

1. Create a project
2. Connect a Git repository
3. Create or import a manual test case
4. AI analyses it into a **Test Model / IR**
5. The Playwright adapter generates source code from the IR
6. The user reviews and edits the code
7. The user commits and pushes to Git
8. The user runs the test
9. Specra shows the result with its evidence
10. On failure, AI analyses the evidence and proposes a fix
11. The user reviews the diff and applies it
12. The test runs again

Features that do not serve this path wait.

## The five principles

Each of these is enforced somewhere — a test, a lint rule, or a schema — not just asserted.

1. **Git-native.** Git owns the automation source code. The database owns metadata. If
   Specra disappeared tomorrow, every user still has a Playwright project that runs on its
   own with `npx playwright test`. See [07 — Git](07-git.md).
2. **Test-Model-native.** Intent is captured as an engine-independent IR. Code generation
   is a projection of the IR, never the primary artefact of the AI. See
   [02 — Test Model / IR](02-test-model-ir.md).
3. **Playwright-native for MVP.** One adapter, one runner, done properly — not four
   half-adapters. The abstraction exists so that adding Selenium later is a package, not a
   rewrite. See [06 — Execution](06-execution.md).
4. **AI-native, in five roles**: understand, model, generate, analyse, repair. Not one
   prompt that emits a `.spec.ts`. See [08 — AI pipeline](08-ai-pipeline.md).
5. **Human-in-the-loop.** AI proposes; a person approves; the approval is what produces a
   commit. There is no code path where a model writes to a user's branch unattended.

## What Specra is not

Do not extend the product into any of these. If a request seems to need one, say so and
build the golden path instead.

| Not building                                          | Because                                                             |
| ----------------------------------------------------- | ------------------------------------------------------------------- |
| A Jira / TestRail clone                               | We integrate with test management, we do not become it              |
| A GitHub clone                                        | Git is the user's, hosted where they already host it                |
| Selenium / Cypress / WebdriverIO / Appium runners     | The IR is designed for them; MVP ships one                          |
| Mobile automation                                     | Same                                                                |
| Visual regression, performance, API testing platforms | Different products entirely                                         |
| Enterprise RBAC                                       | Workspace + member + role is enough until a customer says otherwise |
| Billing, marketplace                                  | Not a product problem yet                                           |

## The failure mode to avoid

The easy version of this product is: manual test case → LLM → Playwright file. That is a
wrapper, it degrades the moment a locator changes, and anyone can build it in a weekend.

The product is the loop **around** that: a structured model of intent, deterministic code
generation from it, real execution with real evidence, and analysis that can point at the
step that broke and propose a scoped change. Every architectural decision in this folder
exists to keep that loop possible.
