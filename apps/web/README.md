# specra-web

Next.js 16 (App Router), talking to [apps/api](../api).

## Stack

| Area         | What is used                                                              |
| ------------ | ------------------------------------------------------------------------- |
| Framework    | Next.js 16, React 19, React Compiler enabled                              |
| i18n         | next-intl 4 — `/vi` · `/en` path prefixes, ICU messages                   |
| Styling      | Tailwind CSS 4, shadcn/ui (base `radix`, preset `nova`), `tw-animate-css` |
| Icons        | lucide-react                                                              |
| Server state | TanStack Query 5 + Devtools                                               |
| Tables       | TanStack Table 9 (the new `tableFeatures` / `useTable` API)               |
| Forms        | TanStack Form 1 + Zod 4 through Standard Schema                           |
| Client state | Zustand 5 (`persist`)                                                     |
| Toasts       | sonner                                                                    |
| Theming      | next-themes (light · dark · system)                                       |
| Tests        | Vitest + Testing Library, Playwright                                      |
| Quality      | ESLint 9 (flat config), Prettier + Tailwind plugin, TypeScript strict     |

## Commands

```bash
pnpm dev          # dev server (/ redirects to a locale)
pnpm build        # production build
pnpm lint         # eslint
pnpm format       # prettier --write
pnpm typecheck    # next typegen && tsc --noEmit
pnpm test         # vitest
pnpm test:e2e     # playwright — builds and serves on :3100 itself
pnpm gen:api      # generate src/types/api.d.ts from the running API's OpenAPI schema
```

## Configuration

`.env.local` (see [.env.local.example](.env.local.example)):

```dotenv
NEXT_PUBLIC_API_URL=http://localhost:8080
```

## Layout

Organised by feature, not by file type. A feature owns its queries, its components and its
schemas; `components/` holds only what more than one feature uses.

```text
src/
├── app/
│   └── [locale]/                  every route is under a locale segment
│       ├── layout.tsx             root layout: <html lang>, fonts, providers
│       ├── error.tsx not-found.tsx
│       ├── (marketing)/           public: landing page, thin header/footer
│       ├── (auth)/sign-in/        centred, chrome-free
│       └── (app)/                 signed-in shell: sidebar + header + ⌘K palette
│           ├── dashboard/  notes/  notes/[id]/  chat/
│           └── settings/{,appearance,language}/
├── i18n/
│   ├── routing.ts                 locales, default, prefix strategy
│   ├── navigation.ts              locale-aware Link / useRouter / usePathname
│   ├── request.ts                 loads the bundle for the request
│   └── messages.test.ts           key + placeholder parity across bundles
├── messages/{en,vi}.json          every user-facing string
├── features/
│   ├── notes/{api,components,schemas.ts}
│   ├── chat/{api,components}      includes the SSE parser and its tests
│   ├── auth/{store.ts,components} local stand-in session
│   ├── settings/components
│   └── dashboard/components
├── components/
│   ├── ui/                        shadcn (28 components)
│   ├── layout/                    sidebar, header, breadcrumbs, nav-user, command palette
│   ├── common/                    page-header, empty-state, error-state, provider-badge
│   ├── theme/  i18n/              theme toggle, locale switcher
│   └── providers.tsx              QueryClient, theme, tooltip, toaster
├── lib/
│   ├── api/{client.ts,types.ts}   fetch wrapper, ApiError, problem-document types
│   ├── config/{site.ts,navigation.ts}
│   └── utils.ts
├── hooks/  stores/  styles/  types/
├── proxy.ts                       locale routing at the edge
└── global.d.ts                    types every t("…") against messages/en.json
```

## Notes

**Pages are thin.** A `page.tsx` resolves params, calls `setRequestLocale`, and renders a view
from `features/`. Logic in a page cannot be tested without a router.

**Import navigation from `@/i18n/navigation`.** `Link`, `useRouter` and `usePathname` there add
and strip the locale prefix; the `next/link` versions do not, and would drop the user out of
their language. `useParams` and `useSearchParams` still come from `next/navigation`.

**Strings live in `messages/`, and `global.d.ts` types them.** `t("notes.form.titel")` is a
compile error, not a key rendered literally on screen. `src/i18n/messages.test.ts` then checks
that every bundle has the same keys _and_ the same ICU placeholders.

**Validation schemas are built from a translator, not declared at module scope.** A
module-level `z.object` would capture whichever language loaded first and keep showing it.

**Dates go through `useFormatter`, never `toLocaleString()`.** The formatter uses the request's
locale and the timezone pinned in `i18n/request.ts`, so server and client markup agree.

**Hand-written vs generated types.** `lib/api/types.ts` is what the code uses. `pnpm gen:api`
writes `types/api.d.ts` from the live schema for cross-checking — springdoc marks every field
optional (Java records emit no `required`), so the generated version is looser.

**Server state and client state stay separate.** Anything belonging to the server lives in
TanStack Query; Zustand holds UI preferences only. Nothing is synced between the two.

**Sorting and paging happen on the server.** The table runs with `manualSorting`, and the
sorting state becomes Spring Data's `sort` parameter (`updatedAt,desc`).

**The SSE parser is hand-written.** `EventSource` cannot POST, so `streamChat` reads the
`ReadableStream` directly and splits frames itself. The API sends each token as a JSON string —
see the explanation in the [root README](../../README.md#decisions-worth-knowing-about).

**Errors carry a reference.** The axios request interceptor sends an `X-Request-Id`, the API echoes it and repeats
it in the problem document, and `ErrorState` shows it — so a screenshot is enough to find the
matching server log line.
