# 05 — API contracts

REST over `/api/v1`, JSON, RFC 9457 problem documents for every error, `X-Request-Id` in and
out, one springdoc document at `/v3/api-docs`. None of that is new — see the `specra-api`
skill for how it is wired.

What follows is the surface the golden path needs. Anything not on this list is not needed
yet.

**Built and planned are marked, because a contract document that reads as if all of it exists
is a map to a building half of which has no floor.** Sections headed _planned_ are the shape
agreed for a milestone that has not landed; they are subject to what contact with the code
teaches, exactly as `POST …/model` was.

Two shapes below already differ from what shipped, and the code is right:

- **`/generations/{id}` is `/code-generations/{id}`.** Code proposals got their own table and
  their own endpoints (V11); a single generic generations resource would have had to be a union
  of three unrelated payloads. `ai_generations` remains the audit row underneath all of them.
- **`POST …/model` answers 200, not 202.** One model call is seconds, and a reviewer wants the
  document rather than a ticket to poll for it. The streaming form is a later addition, not a
  correction.

## Shape rules

- **A controller delegates once.** Two service calls in a handler means the use case has no
  home; give it one.
- **Lists are `PageResponse`**, always paged, never a bare array.
- **A client branches on `code`**, never on `title` or `detail` — those are translated per
  request.
- **Long work returns `202` with a resource to poll or stream**, never a held-open request.
  Generation, inspection and runs are all long work.

## Projects and setup — built

```http
GET    /api/v1/workspaces
POST   /api/v1/workspaces
GET    /api/v1/workspaces/{id}
PUT    /api/v1/workspaces/{id}
DELETE /api/v1/workspaces/{id}
GET    /api/v1/workspaces/{ws}/projects
POST   /api/v1/workspaces/{ws}/projects        { name, key, engine: "PLAYWRIGHT" }
GET    /api/v1/projects/{id}
PATCH  /api/v1/projects/{id}
DELETE /api/v1/projects/{id}

PUT    /api/v1/projects/{id}/repository        { provider, remoteUrl, defaultBranch, credentialId }
POST   /api/v1/projects/{id}/repository/verify  → 200 with branch list, or a problem
GET    /api/v1/projects/{id}/repository
DELETE /api/v1/projects/{id}/repository
```

Auth, membership and BYOK were not in this document's first draft and are now load-bearing:

```http
POST   /api/v1/auth/register · login · refresh · logout · change-password
GET    /api/v1/auth/me · PATCH /api/v1/auth/me
GET    /api/v1/auth/sessions · DELETE /api/v1/auth/sessions/{id}
GET    /api/v1/roles                                        the permission vocabulary
GET    /api/v1/workspaces/{ws}/members · POST · PUT {userId}/role · DELETE {userId} · DELETE /me
GET    /api/v1/workspaces/{ws}/ai-account · PUT · DELETE     BYOK; the key is write-only
GET    /api/v1/workspaces/{ws}/git-credentials · POST · DELETE {id}
```

## Environments — built

```http
GET    /api/v1/projects/{id}/environments      not paged: a project has a handful
POST   /api/v1/projects/{id}/environments      { name, baseUrl, isDefault?, variables[] }
GET    /api/v1/environments/{id}
PUT    /api/v1/environments/{id}               replaces the whole variable list, in order
DELETE /api/v1/environments/{id}
```

A variable is `{ key, value?, secret? }`. **A secret's value is never returned** — a read gives
`{ key, value: null, secret: true, valueSet: true }`, so `valueSet` is how a client shows
"configured" without the API deciding whether this caller may be told a password. Sending a
secret back **without** a value keeps the stored one, which is what lets a form be re-saved
without retyping every credential; a brand-new secret with no value is a 409 rather than an
empty secret stored silently.

At most one environment per project is the default, and the first one created becomes it — a
project with environments but no default would make every run ask a question with one answer.

## Test cases — built

```http
GET    /api/v1/projects/{id}/test-cases        ?q=&status=&tag=&page=&size=
POST   /api/v1/projects/{id}/test-cases
GET    /api/v1/test-cases/{id}
PUT    /api/v1/test-cases/{id}
DELETE /api/v1/test-cases/{id}
POST   /api/v1/test-cases/{id}/index           re-embed this case for the assistant
```

Importing from a test management tool is its own surface, behind one port with Xray as the
first provider:

```http
GET    /api/v1/projects/{id}/test-management · PUT · DELETE · GET /verify
GET    /api/v1/projects/{id}/test-management/tests
GET    /api/v1/projects/{id}/test-management/tests/{externalId}
GET    /api/v1/projects/{id}/test-management/tests/{externalId}/test-case
POST   /api/v1/projects/{id}/test-management/tests/{externalId}/import
```

Editing a test case that already has a model sets `outOfDate` on the response — the UI shows
"regenerate" from that flag, it does not compute staleness itself.

## The AI pipeline

Each stage is separately addressable. That is the point: a user can inspect the IR before any
code exists, and a bad generation is re-run at the stage that went wrong rather than from the
top.

```http
POST   /api/v1/test-cases/{id}/model           → 200, the stored version  (understand → IR)
                                                 (202 + stream is the later, async form)
GET    /api/v1/test-cases/{id}/model                                       (current IR)
GET    /api/v1/test-cases/{id}/model/versions
PUT    /api/v1/test-cases/{id}/model           a human edits the IR directly

POST   /api/v1/test-cases/{id}/code             → 200, the proposal + its impact
GET    /api/v1/test-cases/{id}/code                 the live proposal, if any
GET    /api/v1/test-cases/{id}/code/history
POST   /api/v1/code-generations/{id}/apply      { message?, push?, edits? } → commit, and optionally push
POST   /api/v1/code-generations/{id}/reject
```

`apply` is the only endpoint that writes model output into a working copy, it requires a
generation that is in `PROPOSED`, and it records who decided. There is no endpoint that
generates and applies in one call — see [08](08-ai-pipeline.md).

`apply` also takes `edits`: `[{ path, contents }]`, the bodies a reviewer corrected. They ride
on the request rather than being saved against the proposal, whose stored files are immutable —
the row records what the model produced, the commit records what the human approved. An edit
naming a path the proposal does not contain is a 400, not a write: apply is not "commit any file
I name".

The SSE progress stream is still open. A proposal's files are read from the proposal itself
rather than through `/automation-tests/{id}/files`, which does not exist: until a generation is
applied there is no automation test to read files from, and afterwards the file is in Git, where
`GET /projects/{id}/git/file` already serves it.

## Page inspection — built

```http
GET    /api/v1/projects/{id}/pages             not paged: a project has a handful
POST   /api/v1/projects/{id}/pages/inspect     { pageName, route, environmentId? } → the page
GET    /api/v1/pages/{id}
PUT    /api/v1/pages/{id}/elements/{name}      a human corrects a locator
```

Two shapes differ from the sketch this section used to carry, and the code is right. The path is
`…/pages/inspect` rather than `…/inspect`, because inspection produces a page object and the
resource it belongs under should say so. And it answers **200 with the page**, not 202: opening one
page takes a few seconds, and a reviewer wants the elements rather than a ticket to poll for.

`route` is a path, not a URL — it is joined to the environment's base URL, so the same page object
is re-inspectable against staging or production without editing it. `pageName` is supplied rather
than derived: inspection has no idea that `/login` is `LoginPage`, and guessing would produce a
page nothing references.

**Re-inspecting replaces the elements rather than merging them.** A merge would keep a locator for
an element the page no longer has, and a page object that quietly accumulates dead entries is how
"the tests were passing yesterday" becomes unanswerable.

An element the planner could not address uniquely is not stored at all: a page-object getter that
can never resolve is worse than its absence, which at least surfaces as an unresolved target on the
next generation.

## Git — built

```http
GET    /api/v1/projects/{id}/git/status        branch, ahead/behind, changed files
GET    /api/v1/projects/{id}/git/diff          ?path=
POST   /api/v1/projects/{id}/git/commit        { message, paths[] }
POST   /api/v1/projects/{id}/git/push
POST   /api/v1/projects/{id}/git/pull
GET    /api/v1/projects/{id}/git/branches
POST   /api/v1/projects/{id}/git/branches      { name, from }
POST   /api/v1/projects/{id}/git/checkout      { branch }
GET    /api/v1/projects/{id}/git/history       ?path=&page=
GET    /api/v1/projects/{id}/git/file          ?path=   one text file from the working copy
PUT    /api/v1/projects/{id}/git/file          { path, content }  a human edits it
```

## Runs — built

```http
POST   /api/v1/projects/{id}/runs   { testCaseIds[] | all, browsers[], environmentId } → 202
GET    /api/v1/projects/{id}/runs
GET    /api/v1/runs/{id}
GET    /api/v1/runs/{id}/stream                SSE — still to come; the UI polls while a run is in flight
POST   /api/v1/runs/{id}/cancel
GET    /api/v1/run-items/{id}
GET    /api/v1/run-items/{id}/artifacts        signed, expiring URLs — never raw bytes
```

## Failure analysis — built

```http
GET    /api/v1/run-items/{id}/analysis         the stored reading, 404 if none was made
POST   /api/v1/run-items/{id}/analysis         classify this failure
POST   /api/v1/run-items/{id}/analysis?reanalyse=true    ignore the stored one and ask again
POST   /api/v1/failure-analyses/{id}/repair    → a PROPOSED CodeGenerationResponse of kind FIX
```

Answers `{ rootCause, confidence, summary, rationale, suggestion, repairable }`. `rootCause` is
one of `LOCATOR_DRIFT`, `TIMING`, `APPLICATION_CHANGED`, `TEST_DATA`, `PRODUCT_BUG`, `UNKNOWN` —
the table in [08](08-ai-pipeline.md).

**`suggestion` is null and `repairable` is false for `PRODUCT_BUG` and `UNKNOWN`**, stripped by
the server rather than left to the model's discretion. `repairable` is the API's answer, not the
client's inference: a UI that worked it out for itself would eventually disagree with the
endpoint that enforces it.

`POST` is idempotent without `reanalyse` — the evidence cannot change once a run has finished, so
a second press returns the stored reading rather than buying a second opinion. Only a **FAILED**
item can be analysed; **ERROR** is a 409, because a run that could not complete says nothing about
the application under test.

`repair` answers a `CodeGenerationResponse` — a row in `code_generations` with `kind = 'FIX'`,
reviewed and applied through the same apply endpoint as any other proposal. There is no separate
"heal" endpoint, because healing is not a separate mechanism, and a second route into a user's
repository is how "a human approves every write" quietly stops being true.

It is refused with 409 in four cases, each of which would otherwise produce a diff worth less
than nothing: the cause is `PRODUCT_BUG` or `UNKNOWN` (`repair.not-repairable`); the spec file the
run executed is no longer in the repository (`repair.spec-missing`); the answer was unusable, an
empty file included (`repair.unusable`); or the model returned the file unchanged, which the
prompt asks it to do when the diagnosis does not survive contact with the code
(`repair.no-change`).

## Error codes

`ErrorCode` is the closed set; each constant owns its status and derives its `type` URI and
message keys from its own name. Below are the ones this surface added beyond the generic
HTTP-shaped codes (`VALIDATION_FAILED`, `RESOURCE_NOT_FOUND`, `CONFLICT`, …).

**Built:**

| Code                                                                                                    | HTTP        | When                                                       |
| ------------------------------------------------------------------------------------------------------- | ----------- | ---------------------------------------------------------- |
| `TEST_MODEL_INVALID`                                                                                    | 422         | the IR failed schema, binding or semantic validation       |
| `TEST_CASE_AMBIGUOUS`                                                                                   | 422         | understanding could not resolve a step to an action        |
| `GENERATION_NOT_PROPOSED`                                                                               | 409         | `apply` on something already applied or rejected           |
| `GIT_AUTH_FAILED`                                                                                       | 502         | the remote rejected the stored credential                  |
| `GIT_PUSH_REJECTED`                                                                                     | 409         | non-fast-forward — the user pulls and retries              |
| `WORKING_COPY_DIRTY`                                                                                    | 409         | a write would overwrite uncommitted edits                  |
| `REPOSITORY_NOT_CONNECTED`                                                                              | 409         | a git operation on a project with no repository            |
| `RUNNER_UNAVAILABLE`                                                                                    | 503         | the toolchain plane did not answer                         |
| `EXTERNAL_TEST_NOT_FOUND`                                                                               | 404         | the bound Jira/Xray project has no such test               |
| `INTEGRATION_AUTH_FAILED` · `_PROVIDER_ERROR` · `_UNAVAILABLE`                                          | 502/503     | the three ways a test management call fails                |
| `INVALID_CREDENTIALS` · `INVALID_TOKEN` · `ACCOUNT_LOCKED` · `ACCOUNT_SUSPENDED` · `EMAIL_ALREADY_USED` | 401/403/409 | local auth                                                 |
| `AI_PROVIDER_ERROR` · `_UNAVAILABLE` · `AI_NOT_CONFIGURED`                                              | 502/503     | the model provider said no, was unreachable, or has no key |

**Planned:**

| Code                         | HTTP | When                                            |
| ---------------------------- | ---- | ----------------------------------------------- |
| `ENVIRONMENT_NOT_CONFIGURED` | 422  | a run needs a parameter no environment supplies |

`TEST_CASE_NOT_FOUND` was in this table and was never built: `ResourceNotFoundException`
takes the resource noun as a translation key, so one `RESOURCE_NOT_FOUND` renders "Test case
… does not exist" in the caller's language. A code per entity would be a vocabulary to
maintain for no decision a client can make with it.
