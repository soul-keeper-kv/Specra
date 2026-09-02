# 05 — API contracts

REST over `/api/v1`, JSON, RFC 9457 problem documents for every error, `X-Request-Id` in and
out, one springdoc document at `/v3/api-docs`. None of that is new — see the `specra-api`
skill for how it is wired.

What follows is the surface the golden path needs. Anything not on this list is not needed
yet.

## Shape rules

- **A controller delegates once.** Two service calls in a handler means the use case has no
  home; give it one.
- **Lists are `PageResponse`**, always paged, never a bare array.
- **A client branches on `code`**, never on `title` or `detail` — those are translated per
  request.
- **Long work returns `202` with a resource to poll or stream**, never a held-open request.
  Generation, inspection and runs are all long work.

## Projects and setup

```http
GET    /api/v1/workspaces
POST   /api/v1/workspaces
GET    /api/v1/workspaces/{ws}/projects
POST   /api/v1/workspaces/{ws}/projects        { name, key, engine: "PLAYWRIGHT" }
GET    /api/v1/projects/{id}
PATCH  /api/v1/projects/{id}
DELETE /api/v1/projects/{id}

PUT    /api/v1/projects/{id}/repository        { provider, remoteUrl, defaultBranch, credentialId }
POST   /api/v1/projects/{id}/repository/verify  → 200 with branch list, or a problem

GET    /api/v1/projects/{id}/environments
POST   /api/v1/projects/{id}/environments      { name, baseUrl, variables[] }
PUT    /api/v1/environments/{id}/variables     secrets write-only; reads return "set" flags
```

## Test cases

```http
GET    /api/v1/projects/{id}/test-cases        ?q=&status=&tag=&page=&size=
POST   /api/v1/projects/{id}/test-cases
GET    /api/v1/test-cases/{id}
PUT    /api/v1/test-cases/{id}
DELETE /api/v1/test-cases/{id}
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

POST   /api/v1/test-cases/{id}/code            → 202, AiGeneration id     (IR → files)
GET    /api/v1/automation-tests/{id}/files
GET    /api/v1/automation-tests/{id}/files/{path}
PUT    /api/v1/automation-tests/{id}/files/{path}   a human edits the code directly

GET    /api/v1/generations/{id}                status, rationale, diff
POST   /api/v1/generations/{id}/apply          → the change lands in the working copy
POST   /api/v1/generations/{id}/reject
GET    /api/v1/generations/{id}/stream         SSE: progress and tokens
```

`apply` is the only endpoint that writes model output into a working copy, it requires a
`generationId` that is in `PROPOSED`, and it records who decided. There is no endpoint that
generates and applies in one call — see [08](08-ai-pipeline.md).

## Page inspection

```http
POST   /api/v1/projects/{id}/inspect           { url, environmentId } → 202
GET    /api/v1/projects/{id}/pages
GET    /api/v1/pages/{id}
PUT    /api/v1/pages/{id}/elements/{name}      a human corrects a locator
```

## Git

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
```

## Runs

```http
POST   /api/v1/projects/{id}/runs   { testCaseIds[] | all, browsers[], environmentId } → 202
GET    /api/v1/projects/{id}/runs
GET    /api/v1/runs/{id}
GET    /api/v1/runs/{id}/stream                SSE: item status transitions, live log
POST   /api/v1/runs/{id}/cancel
GET    /api/v1/run-items/{id}
GET    /api/v1/run-items/{id}/artifacts        signed, expiring URLs — never raw bytes
```

## Failure analysis

```http
POST   /api/v1/run-items/{id}/analyse          → 202, AiGeneration of kind FIX
```

The result is a `FIX` generation carrying a root cause, a rationale and a diff. Applying it
goes through `POST /generations/{id}/apply` like any other proposal — there is no separate
"heal" endpoint, because healing is not a separate mechanism.

## Error codes

New `ErrorCode` values this surface needs, translated in both bundles:

| Code                         | HTTP | When                                                     |
| ---------------------------- | ---- | -------------------------------------------------------- |
| `TEST_CASE_NOT_FOUND`        | 404  |                                                          |
| `TEST_MODEL_INVALID`         | 422  | the IR failed schema, referential or semantic validation |
| `TEST_CASE_AMBIGUOUS`        | 422  | understanding could not resolve a step to an action      |
| `GENERATION_NOT_PROPOSED`    | 409  | `apply` on something already applied or rejected         |
| `GIT_AUTH_FAILED`            | 502  |                                                          |
| `GIT_PUSH_REJECTED`          | 409  | non-fast-forward — the user pulls and retries            |
| `WORKING_COPY_DIRTY`         | 409  | a generation would overwrite uncommitted edits           |
| `ENVIRONMENT_NOT_CONFIGURED` | 422  | a run needs a parameter no environment supplies          |
| `RUNNER_UNAVAILABLE`         | 503  |                                                          |
