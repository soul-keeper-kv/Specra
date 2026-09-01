---
name: specra-architecture
description: "Decide where code goes in Specra and keep the arrows pointing one way — the monorepo's packages, the core/feature split both apps share, the api layering ArchUnit enforces, the ContentStore port, the web feature slice, and what an ArchitectureTest failure means. Use before creating a file, a package or a feature folder, when moving or extracting code, when one feature needs another, when a structural test fails, or when reviewing whether something sits in the right place."
---

# Specra's structure — where a file goes, and why

One repo, two applications and an e2e package. Both applications are organised the same
way: **a shared layer that knows nothing about the domain, and one folder per feature that
owns its whole vertical slice.** The folder a class sits in _is_ the design — there is no
second diagram to keep in your head.

This skill is about placement and direction. The per-technology detail lives elsewhere:
`specra-api` (JPA, Flyway, MapStruct, errors), `specra-web` (Next, TanStack, shadcn),
`specra-ai` (Spring AI), `specra-feature` (the end-to-end checklist for a user-visible
change).

## The repo, one level down

| Path                 | What it is                   | Built by                                |
| -------------------- | ---------------------------- | --------------------------------------- |
| `apps/api`           | Spring Boot service, `:8080` | Maven — **not** a pnpm workspace member |
| `apps/web`           | Next.js app, `:3000`         | pnpm workspace package `specra-web`     |
| `tests/e2e`          | Playwright suite             | pnpm workspace package `specra-e2e`     |
| `docker-compose.yml` | Postgres + pgvector, `:5432` | —                                       |

Two more are planned, and are described before they exist so nothing gets built in the wrong
place — [`docs/architecture/03-module-boundaries.md`](../../../docs/architecture/03-module-boundaries.md):

| Path                  | What it is                                      | Rule                                                       |
| --------------------- | ----------------------------------------------- | ---------------------------------------------------------- |
| `packages/test-model` | The IR contract: schema, types, fixtures        | Imports nothing in this repo. `ajv` is its one dependency. |
| `services/runner`     | Node worker: adapter, codegen, inspect, execute | Stateless. Owns no database. Imports nothing in `apps/`.   |

**The split rule between the two runtimes is one question: does the job need the
Node/Playwright toolchain?** If yes, `services/runner`. If no, `apps/api`. Not "it feels more
like TypeScript". Codegen, DOM inspection and execution are on the Node side because the
output has to be typechecked with the real compiler and Playwright is Node-only; tenancy,
persistence, prompting and Git are on the Java side because that plumbing is already there.

`pnpm-workspace.yaml` lists `apps/web` and `tests/e2e`, so one root `pnpm install` covers
both and there is one lockfile. Root scripts reach the app with
`pnpm --filter specra-web <script>`.

**The e2e package is not part of the product it drives.** It has to be runnable against a
deployed URL (`E2E_BASE_URL`), so nothing in `apps/web` may import it, and it reaches into
the app through exactly one door: the `@messages/*` alias onto `apps/web/src/messages`.
Never a relative path into `apps/web`, never a retyped translated string.

## The one idea, in both apps

| Role                             | `apps/api`                   | `apps/web`                                  |
| -------------------------------- | ---------------------------- | ------------------------------------------- |
| Shared, domain-ignorant plumbing | `core/`                      | `components/` · `lib/` · `hooks/` · `i18n/` |
| One folder per feature           | `feature/<name>/`            | `features/<name>/`                          |
| Entry points, thin               | `feature/*/web/` controllers | `app/[locale]/**/page.tsx`                  |
| Wiring and settings              | `config/`                    | `lib/config/` · `components/providers.tsx`  |

The test for "does this belong in the shared layer": **a class or component a second
feature would need belongs in the shared layer; one that names a feature belongs in that
feature** — even if only one feature uses it today.

## apps/api — three folders, arrows one way

`web/ → service/ → domain/`, with `core` underneath and no arrow back up. No ports, no
adapters, no hexagon: a service is a class, a repository is a Spring Data interface, a
feature is a folder with three or four folders in it.

| You are writing…                          | it goes in                                        |
| ----------------------------------------- | ------------------------------------------------- |
| a `@RestController`                       | `feature/<name>/web/`                             |
| a class that owns a rule or a transaction | `feature/<name>/service/`                         |
| an `@Entity` or a Spring Data repository  | `feature/<name>/domain/`                          |
| a MapStruct mapper                        | `feature/<name>/mapper/` — a service collaborator |
| a `@Tool` class the model may call        | `feature/<name>/tool/` — likewise                 |
| a request/response record                 | `feature/<name>/dto/`                             |
| an event other features react to          | `feature/<name>/service/<Name>Events.java`        |
| a `@Configuration` or a property class    | `config/`                                         |
| anything a second feature would need      | `core/`                                           |

Four consequences worth stating, because they are where the shape usually slips:

- **A controller delegates once.** Two service calls in one handler is a use case with no
  home — give it one, in a service. HTTP stops at the controller: nothing below `web/` may
  see `jakarta.servlet` or `org.springframework.web`, so a service works the same whether a
  request, a test or a scheduler called it.
- **A service hands back a DTO, never an entity.** An entity that reaches the web layer is
  how a lazy JPA proxy ends up being serialised.
- **A feature may use another feature, never in a circle.** `note → ai` is fine; `ai → note`
  as well and neither can be changed alone.
- **Where the caller only _reacts_ to a change, use an event.** `NoteService` publishes
  `NoteEvents.NoteContentChanged`; `NoteIndexService` listens `AFTER_COMMIT`. Plain CRUD
  therefore keeps working with the AI stack switched off, and nothing is indexed for a
  transaction that rolled back. Do not wrap a model or network call in `@Transactional`.

Dependencies arrive **through the constructor** — never `@Autowired` on a field. That is
what keeps a class buildable with `new` in a unit test.

## The one deliberate abstraction: `core/content`

`ContentStore` is the single interface-with-implementations in the codebase, and it earns
its place: the AI tools must not know where user content lives, or adding a second backing
store means rewriting the tools and the prompts with it. `ContentTools` holds a
`ContentStore`, never a `NoteService`.

- The implementation lives in the **feature that owns the storage** —
  `feature/note/service/NoteContentStore` — not in `core/`. `core/` owns the port, features
  own the adapters.
- To add one: implement `ContentStore`, return a new `kind()`, annotate `@Component` and
  `@Validated`. `ContentStoreRegistry` discovers it from the context; there is no list to
  edit. Declare what it can do in `capabilities()` — the four mutating methods default to
  refusing, so a read-only store is three methods and no dead overrides.
- Selection is configuration (`specra.content.default-kind`), never a branch in code — the
  same pattern as `spring.ai.model.*`. Both failure modes (two stores claiming one kind, a
  configured default nobody answers to) fail the boot, not the first call.

**Do not generalise this into a ports-and-adapters layer for the rest of the code.** One
port exists because one thing genuinely has to swap; a `NoteServicePort` in front of a class
with one implementation buys nothing, and contradicts the plain design above.

## apps/web — the same shape, without a compiler to enforce it

```text
src/
├── app/[locale]/   (marketing) · (auth) · (app) route groups — layouts and thin pages
├── features/       notes · chat · auth · settings · dashboard
├── components/     ui/ (shadcn) · layout/ · common/ · theme/ · i18n/ · providers.tsx
├── lib/            api/ (client, types) · config/ (site, navigation) · utils
├── i18n/ messages/ hooks/ stores/ styles/ types/
└── proxy.ts        locale routing at the edge (Next 16's name — not middleware.ts)
```

- **A page resolves params, calls `setRequestLocale`, and renders a view from `features/`.**
  Logic in a page cannot be tested without a router. `generateMetadata` is the one other
  thing a page may hold.
- **A feature owns its slice**: `api/` (query keys, hooks, fetchers), `components/`,
  `schemas.ts`, and a store where it has client state (`features/auth/store.ts`).
- **A feature's public surface is its `api/` and its store.** `dashboard` composing
  `useNotes` and `useProviders` is the intended shape. Importing another feature's
  `components/` is not — move the component to `components/common/` instead.
- **`components/` is for what more than one feature uses**; `components/ui/` is shadcn's
  output, edited only when a variant is genuinely missing.
- **`lib/` holds no React state.** `lib/api/client.ts` is the only place `fetch` is called
  and the only place `ApiError` is constructed; feature hooks call `apiFetch`, never `fetch`.
- **A new page is registered in `lib/config/navigation.ts`**, which the sidebar, the command
  palette and the breadcrumbs all read — otherwise the page exists and nobody can reach it.

## When `ArchitectureTest` fails

ArchUnit runs inside `./mvnw test` (no Docker needed):

```bash
cd apps/api && ./mvnw -B test -Dtest=ArchitectureTest
```

A failure reads as "this dependency points the wrong way", and it is a placement bug, not a
rule to relax. **Move the class.** The usual causes:

| The message says                     | What actually happened                                                                                                                        |
| ------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `core` depends on `feature`          | a shared class was written knowing a domain — push it down into the feature, or invert it with a port only if a second implementation is real |
| a slice cycle between features       | two features call each other — one direction becomes an event                                                                                 |
| `Domain` accessed by `Web`           | a controller took an entity or a repository — go through the service, return a DTO                                                            |
| something depends on a `*Controller` | shared work sits in an entry point — move it into a service                                                                                   |
| a vendor package is imported         | see invariant 1 below                                                                                                                         |

Adding an exception to `ArchitectureTest` is a design change: raise it, do not slip it in
with a feature.

## Structural invariants

1. **No LLM vendor is named in code.** `spring.ai.model.chat` / `spring.ai.model.embedding`
   choose the provider at runtime. No `@Qualifier("anthropic")`, no `new OpenAiChatModel(…)`,
   no `if (provider.equals(…))`. Importing `org.springframework.ai.<vendor>` fails the build.
2. **An entity change comes with a Flyway migration** — `ddl-auto: validate` fails startup
   otherwise. Never write one for `vector_store` or `SPRING_AI_CHAT_MEMORY`: Spring AI creates
   those, and the vector width follows the active embedding model.
3. **No user-facing string in a component or a Java class** — `src/messages/*.json` and
   `i18n/messages*.properties`, both languages, or the parity tests fail.
4. **Errors are RFC 9457 documents from `ProblemFactory`**, thrown as a `BusinessException`
   with an `ErrorCode`. Never an ad-hoc body, never a bare `ResponseEntity.status(…)`.
5. **`Link`, `useRouter`, `usePathname` come from `@/i18n/navigation`**, never from
   `next/link` or `next/navigation`.
6. **No execution engine is named outside the adapter.** Playwright vocabulary — `page`,
   `locator`, `getByRole`, `@playwright/test` — belongs in
   `services/runner/src/adapters/playwright/` and in `tests/e2e`. Never in the IR, never in
   `apps/api`, never in `apps/web`. Same containment idea as the vendor rule above, enforced
   by ArchUnit on the Java side and an eslint restricted-import rule on the Node side.
7. **Git holds the automation source code; the database holds metadata.** Content hash and
   commit sha, not file bodies. See
   [`docs/architecture/07-git.md`](../../../docs/architecture/07-git.md).

## Adding a whole new area

- **A new api feature**: copy the shape of `feature/note/` — `domain/`, `dto/`, `mapper/`,
  `service/`, `web/`. There is nothing to register: Spring finds it, and ArchUnit's rules are
  written against `feature.*`, so they apply the moment the folder exists.
- **A new web feature**: a folder under `features/`, a thin page under the right route group,
  an entry in `lib/config/navigation.ts`, and keys in both message bundles.
- **A new package in the workspace**: add it to `pnpm-workspace.yaml`, give it its own
  `package.json` and `tsconfig.json`, and decide deliberately which direction may import
  which — `tests/e2e` is the worked example of a package that depends on the app without the
  app depending on it.
- **Anything to do with the IR, the adapter or the runner**: the `specra-testmodel` skill,
  and do not start by writing code — the schema is the contract three consumers share.

**`tests/e2e` and `services/runner` are not related.** One drives Specra, the other drives the
user's application under test. They both use Playwright and they share no code; a helper that
looks useful to both belongs to neither.
