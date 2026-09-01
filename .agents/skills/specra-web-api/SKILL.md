---
name: specra-web-api
description: "Call the Specra API from apps/web — the axios instance and its interceptors, the http.* helpers, React Query hooks and query keys, ApiError and RFC 9457 problem documents, cancellation, paging and filters, the SSE exception, and how to test the transport. Use whenever adding or changing a query, a mutation, an endpoint call, a feature's api/ folder, or anything that reads or writes server state from the frontend."
---

# Calling the API from `apps/web`

Two rules cover almost everything:

1. **Server state lives in TanStack Query.** Never in `useState`, never copied into Zustand.
2. **HTTP goes through the axios instance in `src/lib/api/client.ts`.** Never `fetch`, never a
   bare `axios` import in a feature.

Everything below is the detail behind those two.

## The layers

```text
component
   │  calls a hook, reads { data, isPending, error }
   ▼
features/<feature>/api/<feature>.ts     useQuery / useMutation + the key factory
   │  calls http.get / post / put / patch / delete
   ▼
lib/api/client.ts                       one axios instance + two interceptors
   │
   ▼
Specra API                              RFC 9457 problem documents
```

A component never imports `http`. A hook never imports `axios`. That is the whole boundary,
and `specra-architecture` states it as a rule: `lib/` holds no React state, features hold no
transport.

## Writing a hook

```ts
"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { http } from "@/lib/api/client";
import type { PageResponse, Project, ProjectInput } from "@/lib/api/types";

export const projectKeys = {
  all: ["projects"] as const,
  lists: () => [...projectKeys.all, "list"] as const,
  list: (workspaceId: string, query: ProjectQuery) =>
    [...projectKeys.lists(), workspaceId, query] as const,
  details: () => [...projectKeys.all, "detail"] as const,
  detail: (id: string) => [...projectKeys.details(), id] as const,
};

export function useProjects(workspaceId: string | undefined, query: ProjectQuery) {
  return useQuery({
    queryKey: projectKeys.list(workspaceId ?? "", query),
    queryFn: ({ signal }) =>
      http.get<PageResponse<Project>>(`/api/v1/workspaces/${workspaceId}/projects`, {
        signal,
        params: { q: query.q || undefined, page: query.page ?? 0, size: query.size ?? 20 },
      }),
    enabled: Boolean(workspaceId),
    placeholderData: (previous) => previous, // no flash while paging
  });
}

export function useCreateProject(workspaceId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: ProjectInput) =>
      http.post<Project>(`/api/v1/workspaces/${workspaceId}/projects`, input),
    onSuccess: (project) => {
      qc.setQueryData(projectKeys.detail(project.id), project);
      void qc.invalidateQueries({ queryKey: projectKeys.lists() });
    },
  });
}
```

Checklist for every hook:

- **`http.*`, not `api.request`, not `fetch`.** The helpers already return `response.data`, so
  nothing downstream ever sees an `AxiosResponse`.
- **Pass `signal`.** `queryFn: ({ signal }) => http.get(url, { signal })` — a query whose
  component unmounted is aborted instead of holding a connection open.
- **Query strings are `params`, never string concatenation.** Axios drops an `undefined` param,
  which is exactly how an empty filter stays off the URL: `q: query.q || undefined`.
- **Keys come from the feature's key factory** (`projectKeys`, `testCaseKeys`, `memberKeys`,
  `authKeys`, `aiKeys`, `workspaceKeys`, `aiAccountKeys`). A loose array in a component is how
  an invalidation silently stops matching.
- **A mutation states what it invalidates.** `setQueryData` for the row it just returned,
  `invalidateQueries` for the lists that now contain it.
- **A query is a hook per endpoint, in `features/<feature>/api/`.** A component that needs two
  endpoints calls two hooks; it does not get a combined one unless the combination is itself a
  concept (`useActiveWorkspace`).

`retry` is configured once in `providers.tsx` and already skips 4xx — do not re-specify it per
hook. `staleTime: Infinity` is right for a deployment constant (`useRoles`), not for data a
user edits.

## What the client already does for you

`src/lib/api/client.ts` exports the instance (`api`), the helpers (`http`), `ApiError`,
`API_URL`, `REQUEST_ID_HEADER`, `REFRESH_PATH` and `streamHeaders()`.

**The request interceptor** stamps three things onto every call, so no call site sets them:

| Header            | From                                    | Why it is not optional                                       |
| ----------------- | --------------------------------------- | ------------------------------------------------------------ |
| `Authorization`   | the stored session's access token       | the API is authenticated; a missing token is a 401           |
| `Accept-Language` | `<html lang>`, set by the locale layout | the API translates problem documents; without it they are en |
| `X-Request-Id`    | generated here                          | it is what ties a user's screenshot to a server log line     |

**The response interceptor** does two things:

- Every failure becomes an **`ApiError`** — including "the API is not running", which arrives as
  `status === 0` (`isNetworkError`), not as an HTTP status.
- A **401 whose `code` is `invalid-token`** triggers one refresh and one replay of the original
  request. The refresh is **single-flight**: the refresh token rotates on use, so two concurrent
  exchanges would make the server see a replay and sign every device out. A failed refresh clears
  the session and lets the original error through; the redirect is the route guard's job, not the
  transport's.

Because that retry is bounded by a `_retried` flag on the config, a hook needs no 401 handling of
its own. Do not add one.

## Errors in the UI

```tsx
// per-field validation, straight from the problem document
serverErrors={mutation.error instanceof ApiError ? mutation.error.fieldErrors : undefined}

// a whole-screen failure
<ErrorState error={query.error} onRetry={() => void query.refetch()} />
```

- **Branch on `code`, never on `message` or `title`** — those are translated per request and
  differ between languages. `code` is stable and is what the API's `ErrorCode` enum emits.
- **A 404 that means "not configured yet" is not an error state.** Catch it in the `queryFn` and
  return `null`, as `useAiAccount` does — a retrying error screen for an expected absence is a bug.
- **`requestId` is worth showing.** `ErrorState` already renders it.

## The one exception: SSE

`streamChat` in `features/chat/api/ai.ts` stays on `fetch` + `ReadableStream`, because axios's
browser adapters resolve only once the whole body has arrived — the opposite of what a token
stream is for. It still must not hand-roll headers: it calls **`streamHeaders()`** from
`client.ts`, which supplies the same credential, locale and request id the interceptor would have.

If you add another streaming endpoint, do the same. Any non-streaming call added to that file goes
through `http.*` like everything else.

## Types

`src/lib/api/types.ts` is hand-written and is what the code imports. `src/types/api.d.ts` is
generated by `pnpm gen:api` (needs the API running) and exists only for cross-checking —
springdoc marks every field optional because Java records emit no `required`, so it is looser than
the truth. Never import from the generated file.

When an endpoint changes shape, the API's DTO and this file change together, in one commit.

## Testing the transport

`src/lib/api/client.test.ts` is the pattern: swap `api.defaults.adapter` (and
`axios.defaults.adapter`, which the refresh exchange uses) for a function that returns canned
responses, then assert on the config objects it recorded. That covers headers, unwrapping, error
mapping and the refresh retry without a network or a mock server.

Feature hooks generally do not get their own transport tests — what is worth testing there is the
component behaviour, and `tests/e2e` covers the rest. Do not write a test that asserts a URL string
a hook builds; assert the behaviour that URL produces.

## Related

- `specra-web` — Next 16, next-intl, theming, TanStack Table v9, shadcn traps
- `specra-feature` — the end-to-end checklist a new user-facing feature must satisfy
- `specra-api` — the other side: RFC 9457 problem documents, `ErrorCode`, message bundles
- `specra-architecture` — which folder a file belongs in and what may import what
