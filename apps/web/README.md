# specra-web

Next.js 16 (App Router), talking to [apps/api](../api).

## Stack

| Area         | What is used                                                              |
| ------------ | ------------------------------------------------------------------------- |
| Framework    | Next.js 16, React 19, React Compiler enabled                              |
| Styling      | Tailwind CSS 4, shadcn/ui (base `radix`, preset `nova`), `tw-animate-css` |
| Icons        | lucide-react                                                              |
| Server state | TanStack Query 5 + Devtools                                               |
| Tables       | TanStack Table 9 (the new `tableFeatures` / `useTable` API)               |
| Forms        | TanStack Form 1 + Zod 4 through Standard Schema                           |
| Client state | Zustand 5 (`persist`)                                                     |
| Toasts       | sonner                                                                    |
| Theming      | next-themes                                                               |
| Tests        | Vitest + Testing Library, Playwright                                      |
| Quality      | ESLint 9 (flat config), Prettier + Tailwind plugin, TypeScript strict     |

## Commands

```bash
pnpm dev          # dev server
pnpm build        # production build
pnpm lint         # eslint
pnpm format       # prettier --write
pnpm typecheck    # next typegen && tsc --noEmit
pnpm test         # vitest
pnpm test:e2e     # playwright (starts the dev server itself)
pnpm gen:api      # generate src/types/api.d.ts from the running API's OpenAPI schema
```

## Configuration

`.env.local` (see [.env.local.example](.env.local.example)):

```dotenv
NEXT_PUBLIC_API_URL=http://localhost:8080
```

## Layout

```text
src/
├── app/
│   ├── layout.tsx          providers + shell
│   ├── page.tsx            Overview
│   ├── notes/page.tsx      list + create
│   ├── notes/[id]/page.tsx edit + index into pgvector
│   └── chat/page.tsx       streaming chat, RAG toggle
├── components/
│   ├── ui/                 shadcn (19 components)
│   ├── notes/              table (TanStack Table) + form (TanStack Form)
│   ├── providers.tsx       QueryClient, theme, tooltip, toaster
│   ├── app-shell.tsx       navigation
│   ├── provider-badge.tsx  shows which LLM is live
│   └── theme-toggle.tsx
├── lib/
│   ├── api/client.ts       fetch wrapper + ApiError carrying fieldErrors
│   ├── api/notes.ts        Query hooks for CRUD
│   ├── api/ai.ts           chat, RAG, and the SSE parser
│   ├── api/types.ts        types matching the API contract
│   └── schemas.ts          Zod schemas
├── stores/ui-store.ts      client state (conversation, streaming, ragMode)
└── types/api.d.ts          generated — do not edit by hand
```

## Notes

**Hand-written vs generated types.** `lib/api/types.ts` is what the code uses.
`pnpm gen:api` writes `types/api.d.ts` from the live schema for cross-checking —
springdoc marks every field optional (Java records emit no `required`), so the generated
version is looser than the hand-written one.

**Server state and client state stay separate.** Anything belonging to the server lives
in TanStack Query; Zustand holds UI preferences only. Nothing is synced between the two.

**Sorting and paging happen on the server.** The table runs with `manualSorting`, and
the sorting state becomes Spring Data's `sort` parameter (`updatedAt,desc`).

**The SSE parser is hand-written.** `EventSource` cannot POST, so `streamChat` reads the
`ReadableStream` directly and splits frames itself. The API sends each token as a JSON
string — see the explanation in the [root README](../../README.md#decisions-worth-knowing-about).
