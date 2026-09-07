---
name: specra-testmanagement
description: "Work on Specra's Jira/Xray integration: encrypted connections, project bindings, external test search with Basic or JQL mode, manual-step decoding and import. Use when changing feature/testmanagement, the integrations screen or the external-test workspace. Not for general Jira issue administration."
---

# Specra test management

Read [the integration blueprint](../../../docs/architecture/10-test-management.md) for
ownership, current endpoints, search semantics and supported scope. It is the reference;
update it when those contracts change. Use `specra-feature` for user-visible changes and
the relevant `specra-api`, `specra-web` or `specra-web-api` skill for implementation.

## Where changes belong

- Backend: `apps/api/src/main/java/dev/specra/api/feature/testmanagement/`. Keep provider
  HTTP, Jira vocabulary, JQL and Xray payload decoding in `XrayTestManagementProvider`.
  The service resolves project access and binding before delegating through
  `TestManagementProvider`; connection storage owns credential encryption.
- Frontend: `apps/web/src/features/testmanagement/`. `TestManagementView` owns connection
  and search; `ExternalTestWorkspace` owns external detail and the handoff to automation.
  Server state uses the existing `api/test-management.ts` hooks and shared HTTP client.
- This work does not belong in `services/runner`. Import feeds the existing local
  test-case/modelling flow; it must not create another code generation or commit path.

## Preserve these contracts

1. The shipped adapter targets Jira Data Center/Xray with a Bearer PAT. Do not assume Jira
   Cloud or Xray Cloud has the same authentication, endpoints or response shapes.
2. Connections belong to workspaces; bindings belong to projects. Resolve a connection
   within the project's workspace and preserve access checks on read and import paths.
   Never return decrypted credentials or put them in prompts, URLs or logs.
3. Browsing reads remote data. Import is explicit and reuses the linked local case on
   repeat calls. Do not silently overwrite local edits or write back to Jira.
4. `advanced=false` means free text; `advanced=true` means native JQL. Keep mode explicit
   through controller, service, provider, frontend types and query key. A text term with
   punctuation must not accidentally become executable JQL.
5. Both modes search only Test issues in the bound project. In JQL mode, group the pasted
   filter before adding that scope and preserve its top-level `ORDER BY`. Quoted values
   containing sorting words are not sorting clauses. Jira owns full grammar validation.
6. A submitted query and an edited draft are different states. New submission resets
   paging; pagination keeps the submitted text and mode. Do not execute partial JQL on
   each keystroke or display old rows as a new query's results.
7. Preserve Xray action/data/expected-result fields, including nested `value.raw` and
   legacy payloads. Missing manual steps on an existing issue are an empty step list,
   not a missing issue. Detail reads still check the bound project.
8. Query rejection is `integration-query-invalid` (400), distinct from remote auth,
   unavailable host and provider errors. Use the existing problem-document path and
   translated bundles; never expose arbitrary upstream error bodies.

## Verify the changed behavior

Use the existing tests as the starting points:

- `XrayTestManagementProviderTest`: mock HTTP responses, assert actual outbound JQL and
  returned paging; cover grouping, quoted values, escaping and upstream failures when
  those paths change.
- `ProblemResponseIT`: preserve English/Vietnamese RFC 9457 error assertions.
- `tests/e2e/specs/jira-search.spec.ts`: submission, pagination, clearing and recovery with
  intercepted API responses; expected labels come from `@messages/*`.

Run the checks required by `AGENTS.md` for code changes and inspect the changed UI in
both languages and themes. Distinguish simulated provider checks from a live Jira test.
For documentation-only work, check links, formatting and consistency with the code.

Keep the blueprint and [API contracts](../../../docs/architecture/05-api-contracts.md)
aligned with query parameters and errors. Autocomplete, saved searches, a visual filter
builder, Cloud providers and result write-back are not shipped capabilities; do not add
them merely to make the integration look more like Jira.
