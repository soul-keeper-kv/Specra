# 08 — AI pipeline

AI has five jobs in this product, not one. Each has a typed input, a typed output and its own
failure mode. Conflating them into one prompt that emits a `.spec.ts` is the failure mode
described in [00](00-product.md).

| Role            | In                                 | Out                         | Deterministic?               |
| --------------- | ---------------------------------- | --------------------------- | ---------------------------- |
| 1 Understand    | manual test case, project glossary | intent, ambiguities, gaps   | no                           |
| 2 Model         | intent + page objects              | a valid IR                  | no                           |
| 3 Plan locators | DOM snapshot + element candidates  | page object elements        | scored, mostly deterministic |
| 4 Generate      | IR + page objects                  | source files                | **yes — no model call**      |
| 5 Analyse       | failure evidence + IR + code       | root cause + a fix proposal | no                           |

Role 4 is the one people expect to be the AI. It is not, and that is the design: once intent
is a validated structure, turning it into code is templating, and templating that compiles
beats prose that sometimes compiles.

## The provider rule still holds

`spring.ai.model.chat` and `spring.ai.model.embedding` choose the provider at runtime. **No
class names a vendor** — no `@Qualifier("anthropic")`, no `new OpenAiChatModel(…)`, no
`if (provider.equals(…))`. `ArchitectureTest` fails the build on a vendor import. Different
roles may use different models (a cheap model for understanding, a stronger one for
analysis); that is configuration, not a branch.

## Which model answers a call

One installation-wide provider is the MVP's simplification, not the destination. Three
things decide the model, and each is data resolved per call — never a branch in code:

```text
        workspace credentials        the account's own key, its own provider, its own budget
                 +
             the role               understand · model · plan · generate · analyse
                 +
          the task's demands        how hard is this one, actually
                 │
                 ▼
        the model for this call
```

**Bring your own key.** A workspace holds its own provider credentials, so each account runs
on its own quota and its own bill. The platform key is the fallback for accounts that have
not set one, and an account with neither is not broken — it is _unconfigured_, which is a
state the product reports and offers to fix. `AiCredentials` is the resolution point today,
answering from configuration; when workspaces exist it asks the workspace first, and no
caller changes.

**Routing by task.** A role gives a floor — locator planning wants a cheap fast model,
failure analysis wants a strong one — and the task can raise it: a twelve-step case with
ambiguous wording is not the same job as a two-step login. The decision is a small,
inspectable function of (role, signals, budget, what the workspace can pay for), and its
choice is recorded on the `AiGeneration` row alongside the token counts, so "was the cheap
model good enough here" becomes a query rather than an argument.

A budget is a constraint on that function, not an exception path: when a workspace is near
its ceiling, routing prefers a cheaper model, and when it is over, calls fail with a distinct
code the UI can explain — never with a generic error, and never by silently doing nothing.

**None of this loosens the vendor rule; it depends on it.** Per-workspace providers are only
possible because no class names one. The routing input is data; the code stays vendor-blind.

## Failure is a reported state, never a crash

The assistant being unavailable must never take anything else down with it, and must never
surface as "something went wrong on our side".

| Situation                         | Code                      | Status |
| --------------------------------- | ------------------------- | ------ |
| No usable credentials             | `ai-not-configured`       | 503    |
| Provider refused (bad key, quota) | `ai-provider-error`       | 502    |
| Provider unreachable or timed out | `ai-provider-unavailable` | 503    |
| Provider rate-limited us          | `rate-limited`            | 429    |

Three rules hold these up:

- **The application boots without any key.** Every non-AI feature works; a warning at startup
  says the assistant is off and why. A missing key is a configuration state, not a failed
  boot.
- **Translation happens at the model-call boundary**, in `AiFailures` — the one place that
  knows a provider was being called. Letting a provider exception reach the global handler is
  what produced the generic 500 in the first place.
- **A stream that has already started cannot send a problem document.** It ends with an
  `error` event carrying the same RFC 9457 body instead, so a client branches on `code`
  identically either way. Credential checks run _before_ the stream opens, so the common case
  is still an ordinary status.

## 1 — Understanding

Reads the test case the way a senior QA would: what is being verified, what state is assumed,
which steps are actually one step, which step is ambiguous.

Its most valuable output is the **ambiguity report**. "Enter username" with no page object
containing a username field is a question, not a guess. Surfacing it as
`TEST_CASE_AMBIGUOUS` with a specific question is better product behaviour than a spec that
fills a wrong field and fails a week later.

## 2 — Modelling

Produces the IR. Constrained hard:

- Output is validated against the schema, then referentially, then semantically
  ([02](02-test-model-ir.md)). Invalid output is rejected, and the rejection is fed back once
  — not looped until something parses.
- Targets must reference existing page objects. The model does not invent selectors; if a
  page has not been inspected, the answer is "inspect it first", not a guess.
- Every step carries `sourceStepIds`. A step that traces to nothing is a hallucinated step
  and validation catches it.

## 3 — Locator planning

Runs against a real DOM snapshot, not against imagination. The DOM is reduced to a candidate
list per interactive element — role, accessible name, label, test id, text, attributes, a
short ancestor path — and each candidate is scored on uniqueness, stability and semantics
([02](02-test-model-ir.md)). The best becomes the element's locator, the runner-up its
fallback.

**Never let the model choose a locator when the DOM is available.** Inspection is cheap and a
model guessing `#login-btn` is the single largest source of flake in tools like this.

## 4 — Generation

Pure projection: `(IR, page objects, options) → files`. Golden-file tests cover it. It runs in
`services/runner` so its output is immediately typechecked and linted
([06](06-execution.md)).

## 5 — Failure analysis

Input is the whole evidence bundle: error and stack, the failed IR step, the generated line,
the DOM at failure, the screenshot, console and network logs, the page object that was used,
and the recent diff of the spec.

Output is a classification and a proposal:

| Root cause          | Typical proposal                                            |
| ------------------- | ----------------------------------------------------------- |
| Locator drift       | update the page object element, using the recorded fallback |
| Timing / race       | replace an implicit wait with a state assertion             |
| Application changed | update the IR, not the code — the test case may need review |
| Test data           | the environment is missing or has a stale value             |
| Genuine product bug | **propose nothing** — say the application is broken         |

That last row matters. A tool that "fixes" every failure by loosening the assertion is worse
than no tool. When the evidence says the application regressed, the correct output is a clear
statement and a link to the evidence, not a diff.

## Regeneration is a diff, not a rewrite

When a test case changes, the pipeline does not regenerate the project:

```text
previous IR  +  new IR  ──► structural diff (step-level, by id)
                              │
                              ▼
                     impact: which files, which lines, which page objects
                              │
                              ▼
                     minimal change proposal + rationale
```

"Click Login" becoming "Click Sign in" is one element rename, and the proposal should be one
line. If regeneration proposes a whole-file rewrite for a one-step change, the impact
analysis is broken — fix that rather than accepting the noise, because a diff nobody can
review is a diff nobody reviews.

## What AI may never do

1. **Write to a branch.** Every output is an `AiGeneration` in `PROPOSED`. A person moves it
   to `APPLIED`, and that action is what produces a commit.
2. **Apply and run in one step.** There is no endpoint for it ([05](05-api-contracts.md)).
3. **Weaken an assertion to make a test pass.** A proposal that removes or loosens an
   assertion must say so in its rationale, prominently.
4. **Invent a locator when a DOM snapshot exists.**
5. **See a secret.** Environment secret values are never in a prompt. Parameters are
   referenced by name.
6. **Silently retry until something parses.** One structured repair attempt on a validation
   failure, then a clear error the user can act on.

## Auditing

Every call writes an `ai_generation` row before anything is applied: model, provider, token
counts, latency, input checksum, the proposal, the rationale, and the human decision. This is
what makes cost attributable, quality measurable, and "why did my test change" answerable.
