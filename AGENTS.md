# Specra

**An AI test automation IDE.** It turns a manual QA test case into an executable automation
test — while **Git stays the source of truth** for the code and **Playwright stays the
execution engine**.

The users are manual QA/QC people who have test cases and business knowledge but do not write
automation. The output has to be real, reviewable source code, because an automation engineer
maintains it afterwards.

## The backbone — read this before writing anything

```text
Requirement / manual test case
        │
        ▼
   AI understanding
        │
        ▼
   Test Model / IR          ← the abstraction the product turns on; engine-independent
        │
        ▼
  Playwright adapter        ← deterministic projection, no model call
        │
        ▼
   Source code  ──►  Git    ← the user's repository, their history, their code
        │
        ▼
   Playwright runner
        │
        ▼
   Result + evidence (trace, video, DOM, logs)
        │
        ▼
   AI failure analysis
        │
        ▼
   Fix proposal  ──►  a human approves  ──►  commit
```

The easy version of this product is "manual test case → LLM → spec file". That is a wrapper
and it is not what is being built. The product is the loop around it: structured intent,
deterministic generation, real execution with real evidence, scoped repair.

**The golden path**, which every milestone is judged against: create a project → connect a
repository → write a test case → AI models it → code is generated → the user reviews and
commits → the test runs → the result shows evidence → AI analyses a failure → the user
applies a fix → it runs again. Work that does not serve this path waits.

The decisions behind all of it are in [`docs/architecture/`](docs/architecture/README.md).
Read [00 — Product](docs/architecture/00-product.md) and
[02 — Test Model / IR](docs/architecture/02-test-model-ir.md) before the first file;
[09 — Roadmap](docs/architecture/09-roadmap.md) says what is being built now and what the
current `notes`/`chat` scaffold gets replaced by.

## The repo

|                       |                                                                                                           |
| --------------------- | --------------------------------------------------------------------------------------------------------- |
| `apps/web`            | Next.js 16, React 19, Tailwind 4, shadcn/ui, TanStack Query/Table/Form, axios, Zustand, Zod, next-intl    |
| `apps/api`            | Spring Boot 3.5.16, Java 17, Maven, JPA/PostgreSQL, Flyway, Spring AI 1.1.8, pgvector, Micrometer Tracing |
| `packages/test-model` | The IR contract: the JSON Schema, its TypeScript mirror, fixtures, a validator                            |
| `tests/e2e`           | Playwright, its own package — drives the product, is not part of it                                       |
| `tools/notion-clone`  | Standalone script, unrelated to the two apps                                                              |

The fifth landed with M3, in the place
[03 — Module boundaries](docs/architecture/03-module-boundaries.md) reserved for it:

|                   |                                                                         |
| ----------------- | ----------------------------------------------------------------------- |
| `services/runner` | Node/TS: the Playwright adapter — codegen, execution and DOM inspection |

The split rule is one question: **does the job need the Node/Playwright toolchain?** If yes,
`services/runner`. If no, `apps/api`. Nothing else decides it.

**The IR schema is one file, shared literally.** `packages/test-model/schema/` holds it; the
api build copies it onto the classpath and validates against the same bytes. Restating its
rules in Java or TypeScript is how a contract becomes two contracts — mirror the _types_ if
you must, and let the parity tests catch you.

## Files for AI agents

The formats below are shared across tools, not specific to any one of them:

| Path                              | Role                                                | Read by                                                                       |
| --------------------------------- | --------------------------------------------------- | ----------------------------------------------------------------------------- |
| `AGENTS.md` (this file)           | Always-loaded context — **the single source**       | Claude Code, Copilot, Cursor, Codex, Gemini CLI, Zed                          |
| `docs/architecture/`              | The decisions, in detail — read on demand           | everyone                                                                      |
| `CLAUDE.md`                       | One line: `@AGENTS.md`                              | Claude Code                                                                   |
| `.github/copilot-instructions.md` | Points back to `AGENTS.md`                          | Copilot                                                                       |
| `.claude/skills/*/SKILL.md`       | Skills loaded on demand                             | Claude Code, Copilot (scans `.github/skills` and `.claude/skills` by default) |
| `apps/web/AGENTS.md`              | Generated by Next.js, rewritten on every `next dev` | every tool                                                                    |

Edit `AGENTS.md`. Do not edit the files that point at it.

Available skills — **start with `specra-feature`** whenever the task touches anything a user
sees. It holds the decisions that are already settled and the checklist that spans both apps;
the rest are per-area reference it points into.

| Skill                   | Covers                                                                                                                           |
| ----------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| `specra-feature`        | The end-to-end checklist: settled decisions, i18n both sides, errors, states, registration, done criteria                        |
| `specra-architecture`   | Where a file goes: monorepo packages, core/feature split, api layering + ArchUnit, web slice                                     |
| `specra-testmodel`      | The IR, the adapter, the runner: schema changes, actions, locators, determinism                                                  |
| `specra-testmanagement` | Jira/Xray connections, bindings, Basic/JQL search, manual steps and import; [blueprint](docs/architecture/10-test-management.md) |
| `specra-api`            | JPA, Flyway, MapStruct, RFC 9457 errors, message bundles, tracing, tests                                                         |
| `specra-web`            | Next 16, next-intl, theming, TanStack v9, shadcn traps                                                                           |
| `specra-web-api`        | Calling the API: the axios client, React Query hooks, query keys, `ApiError`                                                     |
| `specra-ai`             | Spring AI, provider selection, pgvector RAG, SSE streaming                                                                       |
| `commit-message`        | Conventional Commits matching this repo's commitlint                                                                             |

## Layout

Both apps are organised the same way: a `core`/shared layer that knows nothing about the
domain, and one folder per feature that owns its whole vertical slice.

```text
apps/api/src/main/java/dev/specra/api/
├── config/          @Configuration + SpecraProperties (everything under specra.*)
├── core/
│   ├── error/       ErrorCode, BusinessException, ProblemFactory, GlobalExceptionHandler
│   ├── i18n/        SupportedLocale, MessageResolver, LocalizedText, HttpLocaleResolver
│   ├── logging/     MdcKeys, CorrelationIdFilter, RequestLoggingFilter
│   ├── web/         PageResponse
│   ├── content/     ContentStore + registry: one shape for every kind of user content
│   ├── git/         GitProvider port + its records — no JGit type appears here
│   ├── runner/      RunnerClient port; HttpRunnerClient is the only class that knows it is HTTP
│   └── testmodel/   the IR records + the shared schema, validated by TestModelSchema
└── feature/         one folder per feature, one folder per layer inside it
    ├── workspace/   the tenant boundary; other features check parents through its service
    ├── project/     one project ≡ one Git repository; owns the TC-n reference sequence
    ├── testcase/    the manual case — the first real feature, replacing the notes scaffold
    │   ├── web/     TestCaseController
    │   ├── service/ TestCaseService, TestCaseIndexService, TestCaseEvents, TestCaseContentStore
    │   ├── domain/  TestCase, TestCaseStep (entities), TestCaseRepository
    │   ├── mapper/  TestCaseMapper (MapStruct)
    │   └── dto/     TestCaseRequest/Response, TestCaseSummaryResponse
    ├── git/         the repository connection and everything done to its working copy
    │   ├── web/     GitController, GitCredentialController
    │   ├── service/ GitService, GithubGitProvider (the only JGit importer), WorkingCopies
    │   ├── domain/  GitRepository, GitCredential
    │   └── dto/     RepositoryRequest, CommitRequest, GitStatusResponse, …
    ├── codegen/     the IR projected into files, proposed and applied by a person
    │   ├── web/     CodeGenerationController — generate and apply are separate endpoints
    │   ├── service/ CodeGenerationService, PageObjectCatalogue, CommitMessages
    │   ├── domain/  CodeGeneration (+ its status), CodeGenerationRepository
    │   └── dto/     CodeGenerationResponse, GeneratedFileResponse, ApplyGenerationRequest
    ├── environment/ where a run points, and the variables it carries — secrets write-only
    │   ├── web/     EnvironmentController
    │   ├── service/ EnvironmentService — the only class that decrypts one, for dispatch
    │   ├── domain/  Environment, EnvironmentVariable
    │   └── dto/     EnvironmentRequest/Response, EnvironmentVariableRequest/Response
    ├── pageobject/ what inspection read off a real page; the locators codegen resolves
    │   ├── web/     PageObjectController
    │   ├── service/ PageObjectService — best candidate wins, a person may overrule
    │   ├── domain/  PageObject, PageElement, LocatorStrategy
    │   └── dto/     InspectRequest, PageObjectResponse, ElementUpdateRequest
    ├── analysis/    role 5: why a cell failed. PRODUCT_BUG proposes nothing, on purpose
    │   ├── web/     FailureAnalysisController
    │   ├── service/ FailureAnalysisService, RepairService, EvidenceCollector, the two prompts
    │   ├── domain/  FailureAnalysis, RootCause
    │   └── dto/     FailureAnalysisResponse
    ├── run/         execution, its matrix and its evidence
    │   ├── web/     RunController, ArtifactController — artifacts are signed links, never bytes
    │   ├── service/ RunService, RunExecutor, ArtifactService, ArtifactRetention,
    │   │             AutomationTestService (which spec file holds a case, filled at apply)
    │   ├── domain/  TestRun, TestRunItem, TestArtifact, AutomationTest
    │   └── dto/     RunRequest, RunResponse, RunItemResponse, ArtifactLinkResponse
    └── ai/
        ├── web/     AiController
        ├── service/ ChatService, RagService (read), DocumentIndexService (write), AiProviders,
        │             AiHealthService (probes the provider on a timer, caches the reading)
        ├── tool/    ContentTools — what the model is allowed to call
        └── dto/     AskRequest/AskReply, ChatRequest/ChatReply, ProviderInfo

apps/web/src/
├── app/[locale]/    (marketing) · (auth) · (app) route groups; layouts only
├── i18n/            routing, navigation, request config
├── messages/        en.json, vi.json
├── features/        projects · testcases · testmodel · codegen · runs · analysis · environments ·
│                    git · pages · testmanagement · workspaces · chat · auth · settings ·
│                    dashboard
│                    — each api/ + components/ + schemas
├── components/      ui/ (shadcn) · layout/ · common/ · theme/ · i18n/ · providers.tsx
├── lib/             api/ (client, types) · config/ (site, navigation) · utils
├── hooks/ stores/ styles/ types/
└── proxy.ts         locale routing at the edge

tests/e2e/           its own pnpm package (specra-e2e), not a dependency of apps/web
├── specs/           smoke.spec.ts
├── playwright.config.ts
└── tsconfig.json    @messages/* → apps/web/src/messages — the only path into the app
```

Pages under `app/` stay thin: resolve params, call `setRequestLocale`, render a view from
`features/`. Logic in a page cannot be tested without a router.

End-to-end tests import expected text through the `@messages/*` alias, never with a relative
path into `apps/web`, and never by retyping a translated string. Set `E2E_BASE_URL` to run the
suite against a deployment instead of a local build.

`tests/e2e` drives Specra. `services/runner` drives the **user's** application. They are
unrelated things that both happen to use Playwright — never share code between them.

### Layers in `apps/api` — three folders, and the arrows point one way

`web/ → service/ → domain/`, with `core` underneath and no arrow back up. **A class goes in
the folder its role names** — that is the whole design. There are no ports, adapters or a
hexagon: a service is a class, a repository is a Spring Data interface, and a feature is a
folder with three or four folders in it.

| Folder     | Holds                               | May depend on               |
| ---------- | ----------------------------------- | --------------------------- |
| `web/`     | controllers                         | `service/`, `dto/`, `core/` |
| `service/` | services, events, mappers, AI tools | `domain/`, `dto/`, `core/`  |
| `domain/`  | `@Entity`, Spring Data repositories | `core/` only                |
| `dto/`     | request/response records            | nothing of its own feature  |

- A **controller** parses, delegates once and shapes the response. Two service calls in one
  handler is a use case that has no home yet — give it one, in a service.
- A **service** owns the rule and the transaction. It never sees `HttpServletRequest`, and it
  hands back a DTO, never an entity.
- A **feature may use another feature**, never in a circle. Where the calling feature only
  reacts to a change — test cases keeping their embeddings in step — it publishes a record
  from `<Feature>Events` and the listener runs `AFTER_COMMIT`, so plain CRUD keeps working
  with the AI stack switched off.

`ArchitectureTest` (ArchUnit, part of `./mvnw test`, no Docker) fails the build on each of
these, on a controller or an entity in the wrong folder, and on a class that names an LLM
vendor. A violation means a class is in the wrong place — move it before you reach for an
exception to the rule.

Deciding where a new class, component or package goes — or reading an `ArchitectureTest`
failure — is what the `specra-architecture` skill is for.

## Product invariants — these are the product, not preferences

1. **Git is the source of truth for automation code.** The database stores metadata, a
   content hash and a commit sha — never the authoritative file bodies. The test: clone the
   user's repo, `pnpm install`, `npx playwright test`, and it runs with no reference to
   Specra. Any design that fails that test is wrong.

2. **The IR names no engine.** No `page`, `locator`, `getByRole`, `cy`, `driver` in the Test
   Model. Playwright vocabulary lives only in `services/runner/src/adapters/playwright/`, and
   nowhere else in the repo — the same containment rule as the LLM vendor one.

3. **Code generation is deterministic.** `(IR, page objects, options) → files` is a pure
   function with golden-file tests. **No model call inside the adapter.** The intelligence
   happened earlier, when the IR was built.

4. **AI proposes; a human disposes.** Every model output that could change a repository is an
   `AiGeneration` in `PROPOSED` with a diff. A person moves it to `APPLIED`, and that action
   is what produces a commit. There is no endpoint that generates and applies in one call,
   and no code path where a model writes to a user's branch unattended.

5. **A locator is never guessed when a DOM snapshot exists.** Inspection is cheap; a model
   inventing `#login-btn` is the largest source of flake in tools like this. Locators live on
   page objects, not in the IR, so healing one changes one row.

6. **No secret reaches a prompt, a generated file, or a log.** Environment secrets are
   encrypted at rest, injected into the runner at dispatch, referenced in the IR by name
   only, and masked in captured output.

7. **A fix proposal never weakens an assertion to make a test pass.** When the evidence says
   the application regressed, the correct output is "your app is broken", with the evidence —
   not a diff.

8. **Do not widen the MVP.** No Selenium, Cypress, WebdriverIO, Appium, mobile, visual
   regression, performance or API testing; no Jira/TestRail/GitHub clone; no billing or
   marketplace. The IR exists so the engines are possible later. Building them now costs the
   golden path.

## Stack invariants — breaking these breaks the build, it is not "cleanup"

1. **Never name an LLM vendor in code.** The provider is chosen at runtime by
   `spring.ai.model.chat` / `spring.ai.model.embedding`. No `@Qualifier("anthropic")`, no
   `new AnthropicChatModel(...)`, no `if (provider.equals("openai"))`.

   The same containment rule covers JGit: `core/git` is the port, `GithubGitProvider` is the
   only class that may import `org.eclipse.jgit`, and `ArchitectureTest` fails the build on
   any other importer.

2. **`AiController.asJson(token)` is not redundant.** SSE tokens must be JSON-encoded;
   dropping it silently eats a token's leading space and any newline inside an answer.
   `AiStreamIT` locks this contract down.

3. **Do not upgrade Spring Boot to 4.x.** Spring AI 1.1.8 is built against Boot 3.5.15.
   Initializr only serves 4.x now, which is why `pom.xml` is hand-written — deliberately.

4. **Do not write a Flyway migration for the `vector_store` table.** Spring AI creates it,
   because the vector width follows whichever embedding model is active.

5. **TanStack Table here is v9**, not v8. There is no `useReactTable`, `getCoreRowModel()`,
   or the old `columnHelper`.

6. **`vite` is pinned to 7 and `@vitejs/plugin-react` to 5 — do not take Vite 8.** Vite 8
   depends on `rolldown`, whose Windows binding ships unsigned and with no reputation yet, so
   Smart App Control blocks `rolldown-binding.win32-x64-msvc.node` and every `vitest` run
   dies before it loads a test. Vite 7 bundles with rollup + esbuild, which SAC allows.
   Vitest 4 supports `vite: ^6 || ^7 || ^8`, so staying on 7 costs nothing, and plugin-react
   5 still peers both — the pin is one line to undo once rolldown earns a signature.

7. **Specra's Postgres listens on port 5432** (the default). If another Postgres already
   holds 5432 on a dev machine, set `DB_PORT` in `.env` — do not edit the compose files.

8. **pnpm only, and it is a workspace.** `pnpm-workspace.yaml` lists `apps/web`, so one root
   `pnpm install` covers both packages and there is one `pnpm-lock.yaml`. Never run
   `npm install` or `yarn` here, and never add a `package-lock.json`. Root scripts reach the
   web app with `pnpm --filter specra-web <script>`, not `npm --prefix`.

9. **No user-facing string is written in a component or a Java class.** Web strings live in
   `src/messages/*.json` and are read with `useTranslations` / `getTranslations`; API strings
   live in `src/main/resources/i18n/messages*.properties` and are read through
   `MessageResolver` or a Bean Validation `{key}`. Adding a key to one bundle and not the
   other fails `MessageBundleTest` (api) and `messages.test.ts` (web).

10. **Errors are RFC 9457 problem documents, built only by `ProblemFactory`.** Never return an
    ad-hoc error body or a bare `ResponseEntity.status(...)`. Throw a `BusinessException`
    subclass with an `ErrorCode`; the handler renders it. Clients branch on `code`, never on
    `title`/`detail` — those are translated per request.

11. **In `apps/web`, import `Link`, `useRouter` and `usePathname` from `@/i18n/navigation`,
    never from `next/link` or `next/navigation`.** The next-intl versions add and strip the
    `/vi` · `/en` prefix. A `next/link` href sends the user out of their locale.
    (`useParams` and `useSearchParams` still come from `next/navigation`.)

12. **The edge file is `proxy.ts`, not `middleware.ts`.** Next 16 renamed the convention and
    warns on the old name; next-intl still calls its factory `createMiddleware`.

13. **Do not put `setState` in an effect.** The React Compiler lint rule is an error, not a
    warning. Derive the value, or set it in the event handler that caused it.

14. **`CorrelationIdFilter` runs first and clears the MDC in a `finally`.** Threads are
    pooled; a leaked MDC entry attributes one user's log lines to another request.

## Common commands

```bash
pnpm install        # whole workspace: root tooling + apps/web
pnpm db:up          # Postgres + pgvector, host port 5432
ollama serve        # local chat model — free, no key; VS Code: "specra: ollama up"
ollama pull qwen2.5:7b   # once, ~4.7 GB; the model OLLAMA_MODEL names
pnpm dev:api        # :8080
pnpm --filter specra-runner start   # :8090 — the codegen job server apps/api calls
pnpm dev:web        # :3000  (redirects / to /vi)
pnpm test:api       # unit + integration — needs Docker (unit alone: ./mvnw test)
pnpm test:web       # 20 Vitest tests
pnpm test:runner    # 51 Vitest tests: the adapter, its golden files, the job server, portability
pnpm test:e2e       # 8 Playwright specs; builds apps/web and serves it on :3100
pnpm e2e:browsers   # one-off: download the Chromium build Playwright drives
pnpm typecheck      # web + e2e + test-model + runner: next typegen && tsc --noEmit

pnpm format         # prettier: root docs + apps/web
pnpm format:api     # spotless: google-java-format over apps/api
pnpm lint:api       # spotless:check — what CI enforces
```

## Code style

Nothing about formatting is a review topic; a tool decides it.

| Scope                        | Tool                                           |
| ---------------------------- | ---------------------------------------------- |
| Java, plus api `.yml`/`.sql` | Spotless + google-java-format (`GOOGLE`, 100c) |
| Web sources                  | Prettier + Tailwind plugin, ESLint 9           |
| Root docs and config         | Prettier                                       |
| Indent, EOL, charset         | `.editorconfig`                                |
| Line endings in git          | `.gitattributes` (`eol=lf`)                    |

`spotless:check` is bound to Maven's `validate` phase, so **every** `./mvnw` command fails on
unformatted Java. Run `./mvnw spotless:apply` to fix.

Debugging in VS Code: `.vscode/launch.json`, pick **Full stack (API + Web)**.

## Before reporting work as done

Run it, do not assume:

```bash
cd apps/api && ./mvnw -B verify
cd apps/web && pnpm lint && pnpm typecheck && pnpm test && pnpm build
```
