# 10 — Test management: Jira / Xray

This blueprint describes the current integration, including Basic and JQL search. Specra
consumes external manual tests as inputs to the [golden path](00-product.md); it does not
become an issue tracker. The shipped provider is **Jira Data Center with Xray**, using a
personal access token. Jira Cloud and Xray Cloud are not interchangeable with this adapter.

## Ownership and flow

```text
Workspace connection (encrypted credentials)
        │
Project binding (connection + remote project)
        │
Browse / search Jira → read issue and Xray manual steps
        │
Explicit import → linked Specra test case
        │
Model / IR → deterministic code → review → commit → run → evidence
```

Jira/Xray owns the source issue. Browsing does not copy every search result into the
database. Import creates the local automation input with external source, issue key, URL
and import timestamp. Once imported, the automation workspace uses the local case's steps
for modelling and coverage. Repeating import reuses the existing linked case; it does not
silently refresh a person's local edits from Jira.

Git still owns automation source code. Importing or searching does not generate code,
apply a proposal, commit, or write anything back to Jira.

## Components and storage

| Owner                                                         | Responsibility                                                                    |
| ------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| `feature/testmanagement/web`                                  | Connection and project-binding REST controllers                                   |
| `TestManagementConnectionService`                             | Workspace access, connection storage, credential encryption and materialization   |
| `TestManagementService`                                       | Project access, binding, provider delegation and import through `TestCaseService` |
| `TestManagementProvider` / `TestManagementProviders`          | Provider contract and registry, inside the owning feature                         |
| `XrayTestManagementProvider`                                  | Jira HTTP, JQL construction, Xray step decoding and remote error mapping          |
| `apps/web/src/features/testmanagement/api/test-management.ts` | Query keys and HTTP hooks                                                         |
| `TestManagementView`                                          | Connection form, verification, Basic/JQL search and paging                        |
| `ExternalTestWorkspace`                                       | External detail and the transition into local modelling and code review           |

The integration belongs in `apps/api`: none of its calls needs the Node/Playwright
toolchain. Follow the existing `web → service → domain` boundaries.

V7–V9 established the connection/binding tables and import identity; see
[04 — Database](04-database.md). `test_management_connections` belongs to a workspace and
stores `provider`, non-secret `configuration` and `credentials_cipher`.
`test_management_bindings` links a Specra project to a connection and `remoteProjectId`.
Deleting a connection still in use is rejected. Removing a binding leaves the remote data
and imported cases intact.

Connection management checks workspace permissions. Binding edits check project content
edit permission; browsing checks content view permission. Import also goes through the
test-case service's write checks. Resolve connections within the project's workspace;
a client-supplied connection ID is not proof of access.

Credentials are write-only, encrypted through `SecretsCipher`, and decrypted on the
backend for provider calls. The Xray adapter sends the PAT as a Bearer header. The optional
username is not used for Basic authentication. Never put credentials in a query string,
response, prompt, generated file or log.

## REST contract

All paths below start with `/api/v1`.

| Method and path                                                     | Behavior                                             |
| ------------------------------------------------------------------- | ---------------------------------------------------- |
| `GET /workspaces/{workspaceId}/test-management-connections`         | List connection metadata without credentials         |
| `POST /workspaces/{workspaceId}/test-management-connections`        | Create an encrypted connection                       |
| `DELETE /workspaces/{workspaceId}/test-management-connections/{id}` | Delete an unused connection                          |
| `GET /projects/{id}/test-management`                                | Get binding; 404 means not connected                 |
| `PUT /projects/{id}/test-management`                                | Bind with `{ connectionId, remoteProjectId }`        |
| `DELETE /projects/{id}/test-management`                             | Remove binding                                       |
| `GET /projects/{id}/test-management/verify`                         | Check remote identity, project access and test count |
| `GET /projects/{id}/test-management/tests`                          | Search; parameters below                             |
| `GET /projects/{id}/test-management/tests/{externalId}`             | Read issue and manual steps                          |
| `GET /projects/{id}/test-management/tests/{externalId}/test-case`   | Find imported local case; 404 means not imported     |
| `POST /projects/{id}/test-management/tests/{externalId}/import`     | Import or return the linked local case               |

### Search contract

| Parameter  | Default | Meaning                                                            |
| ---------- | ------- | ------------------------------------------------------------------ |
| `q`        | omitted | Free text in Basic mode; native JQL in advanced mode               |
| `advanced` | `false` | Explicit choice of query language; never infer it from punctuation |
| `page`     | `0`     | Zero-based page, minimum 0                                         |
| `size`     | `20`    | Page size, between 1 and 100                                       |

The response is `PageResponse<ExternalTestSummary>`. Each summary contains `externalId`,
`title`, `status`, `priority`, `labels` and `url`. Totals come from Jira, not the number of
rows in the current page. The adapter maps paging to `startAt = page * size` and `maxResults`.

**Basic** searches summary and description, escaping backslashes and quotes in the term.
It adds an exact key alternative only when the input matches a Jira issue key; arbitrary
words must not become `key = ...`. The default ordering is `key ASC`.

**JQL** accepts a query copied from Jira's advanced search, including functions, groups,
`AND` / `OR`, custom fields and an optional `ORDER BY`. Jira validates field names, values
and syntax; Specra does not maintain a competing JQL grammar. The backend separates the
sorting clause outside quoted values and parentheses, wraps the filter, then adds the
bound-project and Test-type constraints. Unbalanced filter quotes/parentheses are rejected.

For example, in a binding to `QA`, pasting:

```jql
status = "Open" OR assignee = currentUser()
ORDER BY updated DESC
```

produces:

```jql
project = "QA" AND issuetype = Test
AND (status = "Open" OR assignee = currentUser())
ORDER BY updated DESC
```

Parentheses preserve the meaning of `OR` without escaping the project/type scope. Sorting
inside a quoted search value stays in the filter. Without an explicit sort, use `key ASC`;
an empty query lists the bound project's Tests. A pasted query naming a different project
may return no matches because the binding still applies. `currentUser()` is evaluated by
Jira as the stored credential's account, which may differ from the signed-in Specra user.

This is **JQL**, not SQL. Arbitrary SQL and Jira search URLs are not accepted as JQL input.

### UI behavior

Basic and JQL retain separate drafts. Search runs on explicit submission, not each
keystroke. Basic accepts Enter; JQL uses a multiline editor with Ctrl+Enter or Cmd+Enter.
Switching modes alone does not execute a query. A new search resets the page to zero;
paging uses the last submitted query, even when the draft has changed.

Keep query text, mode, page and size in the query key. Pass cancellation through the shared
HTTP client. Do not show a previous query's rows as the result of a new search. Keep loading,
empty, no-match, query-error and provider-error states distinct, with a visible scope hint.
Labels and errors live in both language bundles and use the existing theme tokens.

## Remote reads and errors

The adapter currently uses Jira `/rest/api/2/myself`, `/project/{id}`, `/search` and
`/issue/{key}`, plus Xray `/rest/raven/2.0/api/test/{key}/steps`. A detail read verifies the
issue belongs to the bound project. Preserve the step action, input data and expected
result, including Xray's nested `fields` / `value.raw` representation and legacy shapes.
A 404 from the steps endpoint means no manual steps after the issue itself was found;
it must not turn an existing issue into a missing-test error.

| Failure                                          | Specra code / HTTP                 |
| ------------------------------------------------ | ---------------------------------- |
| Invalid filter structure or Jira search HTTP 400 | `integration-query-invalid` / 400  |
| Remote HTTP 401 or 403                           | `integration-auth-failed` / 502    |
| Issue missing or outside the bound project       | `external-test-not-found` / 404    |
| Network failure or timeout                       | `integration-unavailable` / 503    |
| Other remote failure                             | `integration-provider-error` / 502 |

Render errors through `ProblemFactory` as RFC 9457 documents. Do not forward arbitrary
remote response bodies. The UI branches on `code`; query rejection asks the user to check
syntax, fields and values. A remote authentication failure is not a local session expiry.

## Verification and limits

`XrayTestManagementProviderTest` exercises HTTP decoding, text/key search, escaping,
JQL grouping/sorting, paging and error mapping. `ProblemResponseIT` checks the search error
contract in English and Vietnamese. `tests/e2e/specs/jira-search.spec.ts` uses intercepted
API responses to test submission, paging, clearing and error recovery in both languages
and both themes. These browser tests do not prove connectivity to a real Jira installation.

The current feature does not include JQL autocomplete, a visual filter builder, saved
queries, URL extraction, automatic source refresh, result synchronization back to Jira,
or Jira Cloud/Xray Cloud support. Add those only as separately scoped work; do not describe
them as capabilities of the current search. See [09 — Roadmap](09-roadmap.md).
