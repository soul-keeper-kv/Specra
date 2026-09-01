# Specra

**An AI test automation IDE.** It turns a manual QA test case into an executable automation
test — while Git stays the source of truth for the code and Playwright stays the execution
engine.

```text
manual test case ──► AI ──► Test Model / IR ──► Playwright code ──► Git ──► run ──► result
                                                                                     │
                                   fix proposal ◄── AI failure analysis ◄────────────┘
```

It is built for manual QA/QC people who have the test cases and the business knowledge but do
not write automation — and the output is real, reviewable source code, because an automation
engineer maintains it afterwards. Clone the repository it writes to, `pnpm install`,
`npx playwright test`, and the suite runs with no reference to Specra at all.

The architecture is written down before it is built:
[docs/architecture](docs/architecture/README.md) — start with
[the product](docs/architecture/00-product.md) and
[the Test Model / IR](docs/architecture/02-test-model-ir.md).
[The roadmap](docs/architecture/09-roadmap.md) says what exists today and what is next.

| Folder                                   | Contents                                                                                            |
| ---------------------------------------- | --------------------------------------------------------------------------------------------------- |
| [apps/web](apps/web)                     | Next.js 16 · React 19 · Tailwind 4 · shadcn/ui · lucide · TanStack Query/Table/Form · Zustand · Zod |
| [apps/api](apps/api)                     | Spring Boot 3.5 · Java 17 · JPA + PostgreSQL · Flyway · Spring AI 1.1 · pgvector RAG · springdoc    |
| [tests/e2e](tests/e2e)                   | Playwright suite that drives Specra itself                                                          |
| [tools/notion-clone](tools/notion-clone) | Script that clones an entire Notion workspace to Markdown                                           |

Two properties the stack keeps throughout: **no lock-in to any one LLM vendor** — Anthropic,
OpenAI, Ollama and a locally-run ONNX model all sit on the classpath, and picking one is two
lines of config — and **no lock-in to one execution engine**, because intent is captured as an
engine-independent Test Model and Playwright is one adapter behind it.

It is bilingual end to end (Vietnamese and English), light/dark themed, and traceable: every
response carries a request id and a trace id, and every error is an RFC 9457 problem document
translated into the caller's language.

---

## Quick start

Needs Docker, JDK 17+, Node 20+ and [pnpm](https://pnpm.io/installation) 10+
(`npm install -g pnpm`).

```bash
cp .env.example .env    # add a key if you want a real LLM
pnpm install            # one install for the whole workspace: root tooling + apps/web

pnpm db:up              # Postgres + pgvector (host port 5432)
pnpm dev:api            # http://localhost:8080
pnpm dev:web            # http://localhost:3000
```

This is a **pnpm workspace** ([pnpm-workspace.yaml](pnpm-workspace.yaml)) — a single
`pnpm-lock.yaml` at the root covers both the root tooling and `apps/web`. Do not run
`npm install` in either package; it would write a competing lockfile.

The first API run downloads the ONNX embedding model (~90 MB) into a cache — once only.

| Address                                 |                |
| --------------------------------------- | -------------- |
| <http://localhost:3000>                 | Web            |
| <http://localhost:8080/swagger-ui.html> | API docs       |
| <http://localhost:8080/v3/api-docs>     | OpenAPI schema |
| <http://localhost:8080/actuator/health> | Health         |

---

## Debugging in VS Code

[.vscode/launch.json](.vscode/launch.json) is already set up. Open **Run and Debug** and
pick:

| Configuration                       | What it does                                                    |
| ----------------------------------- | --------------------------------------------------------------- |
| **Full stack (API + Web)**          | Runs both, bringing Postgres up first                           |
| **Full stack (throwaway database)** | Same, but the API uses a disposable Testcontainers database     |
| API: Spring Boot                    | API only, with breakpoints in Java                              |
| API: attach to :5005                | Attaches to an already-running process                          |
| Web: full stack                     | Breakpoints in both server and client components in one session |
| Web: Vitest / Playwright            | Runs tests from the editor                                      |

The API configuration has a `preLaunchTask` that runs `docker compose up -d --wait`,
which **blocks until Postgres reports healthy** — so the API never starts before the
database. That task also creates `.env` from `.env.example` if it is missing.

Java needs the **Extension Pack for Java**; see
[.vscode/extensions.json](.vscode/extensions.json).

---

## Postgres port

Specra maps Postgres to the default **5432**. That port is often already taken by another
Postgres on a dev machine — if `pnpm db:up` cannot bind it, set `DB_PORT` in `.env` to a
free port (`5433`, say); both `docker-compose.yml` and `application.yml` read that variable.

```bash
docker ps --filter publish=5432    # see what is holding 5432
```

---

## Switching LLM provider

No code changes — set environment variables and restart the API:

```bash
AI_CHAT_PROVIDER=openai   OPENAI_API_KEY=sk-...        # or
AI_CHAT_PROVIDER=ollama   OLLAMA_MODEL=llama3.2        # or
AI_CHAT_PROVIDER=anthropic ANTHROPIC_API_KEY=sk-ant-...
```

How it works: every Spring AI starter is on the classpath, but
`spring.ai.model.chat` / `spring.ai.model.embedding` decide which one auto-configures.
The result is exactly one `ChatModel` bean in the context, which
[AiConfig](apps/api/src/main/java/dev/specra/api/config/AiConfig.java) injects by type —
no vendor name appears anywhere in the code.

`GET /api/ai/providers` reports what is currently live; the web app shows it in the
header.

> **Anthropic has no embedding model.** That is why `AI_EMBEDDING_PROVIDER` defaults to
> `transformers` (local ONNX, 384 dimensions, no API key). Switching to `openai` (1536)
> or `ollama` (768) changes the vector width, so the `vector_store` table has to be
> dropped first: `pnpm db:reset`.

---

## Languages and theme

The web app is served under a locale prefix — `/vi` and `/en` — chosen from `Accept-Language`
on the first visit and remembered in a cookie afterwards. Switching from the header, the
settings page or the ⌘K palette rewrites the current path rather than reloading the site, so
the page you are on is preserved.

The API is translated too. The web app forwards the active locale as `Accept-Language`, so
validation messages and error details arrive already in the right language:

```bash
curl 'localhost:8080/api/notes/does-not-exist?lang=vi'
# {"title":"Không tìm thấy","code":"resource-not-found", …}
```

Adding a language: a bundle in `apps/web/src/messages/`, a locale in `apps/web/src/i18n/routing.ts`,
a `messages_xx.properties` in `apps/api/src/main/resources/i18n/`, and a constant in
`SupportedLocale`. Tests on both sides then fail until the new bundle covers every key.

Theme is light / dark / system via next-themes, applied as a class on `<html>` before paint,
with the choice mirrored on the settings page.

---

## End-to-end flow

```text
Create note ──POST /api/notes──▶  PostgreSQL (JPA + Flyway)
                                       │
Index note  ──POST /api/notes/{id}/index──▶  chunk → embed → pgvector
                                       │
Ask (RAG)   ──POST /api/ai/ask──▶  similarity search → into the prompt → LLM answers
                                       │
Chat        ──POST /api/ai/chat/stream──▶  SSE tokens, history in Postgres
```

The workspace maps onto that: **Dashboard** (status), **Notes** (CRUD + indexing), **Chat**
(streaming, RAG toggle), **Settings** (theme, language). A public landing page and a local
sign-in sit outside it.

---

## Code style

Formatting is decided by tools, not by review comments.

| Scope                             | Tool                                             | Config                                                 |
| --------------------------------- | ------------------------------------------------ | ------------------------------------------------------ |
| Java, plus api `.yml`/`.sql`      | Spotless + google-java-format (GOOGLE, 100 cols) | [apps/api/pom.xml](apps/api/pom.xml)                   |
| Web sources                       | Prettier + Tailwind plugin, ESLint 9             | [apps/web/.prettierrc.json](apps/web/.prettierrc.json) |
| Root docs and config              | Prettier                                         | [.prettierrc.json](.prettierrc.json)                   |
| Indent, EOL, charset (any editor) | EditorConfig                                     | [.editorconfig](.editorconfig)                         |
| Line endings stored in git        | gitattributes (`eol=lf`)                         | [.gitattributes](.gitattributes)                       |
| Editor font, format-on-save       | VS Code workspace settings                       | [.vscode/settings.json](.vscode/settings.json)         |

```bash
pnpm format        # prettier: root docs + apps/web
pnpm format:api    # spotless:apply over apps/api
pnpm lint          # eslint
pnpm lint:api      # spotless:check
```

`spotless:check` is bound to Maven's `validate` phase, so **any** `./mvnw` command fails
on unformatted Java — including `./mvnw test`. Fix it with `./mvnw spotless:apply`.

javac runs with `-Xlint:all` (minus the noisy `processing` and `serial`), so compiler
warnings show up in ordinary builds.

---

## Testing

```bash
pnpm test:web     # Vitest — 12 tests: the SSE parser and the message bundles
pnpm test:api     # JUnit — 7 unit + 17 integration (Testcontainers with real pgvector)
pnpm typecheck
pnpm lint
cd apps/web && pnpm test:e2e   # Playwright — 8 tests: routing, locale, theme, palette
                               # (builds and serves on :3100 itself)
```

`test:api` needs Docker running: it starts a real `pgvector/pgvector:pg17` container,
runs Flyway, writes and reads vectors, and checks the SSE wire format over real HTTP.

---

## Decisions worth knowing about

**SSE tokens are JSON-encoded.** The SSE spec requires a receiver to strip one space
after `data:`. Model tokens very often begin with a space (`" world"`) and sometimes
contain a newline — plain SSE framing corrupts both. So
[AiController](apps/api/src/main/java/dev/specra/api/feature/ai/web/AiController.java) sends each
token as a JSON string and the client `JSON.parse`s it. `AiStreamIT` locks that contract
down.

**Flyway owns the business schema; Spring AI creates `vector_store` itself.** Because
the vector width depends on the active embedding model, letting Spring AI create the
table means switching providers needs no new migration. Hibernate runs
`ddl-auto: validate` to catch mapping drift.

**Two separate `ChatClient`s.** `chatClient` has chat memory; `ragChatClient`
deliberately does not, so earlier turns cannot bleed into the retrieved context and be
mistaken for evidence.

**Paging and sorting happen on the server.** TanStack Table runs with `manualSorting`,
and the sorting state translates into Spring Data's `sort` parameter.

**One error shape, translated at the edge.** Every non-2xx response is an RFC 9457 problem
document built by `ProblemFactory`, carrying a stable `code` plus the `traceId` and
`requestId` that identify the matching log line. Services throw a `BusinessException` with a
message _key_; nothing below the controller knows which language it is running for.

**No user-facing string is written in code.** Web strings live in `apps/web/src/messages/*.json`
and API strings in `apps/api/src/main/resources/i18n/messages*.properties` — including Bean
Validation messages, because the validator is bound to the same `MessageSource`. A key present
in one bundle and missing from another fails the build on both sides.

**The locale is in the URL.** `/vi/notes` and `/en/notes` are distinct, indexable pages, so a
shared link keeps its language. The web app sends that locale as `Accept-Language`, which is how
a validation error comes back already translated instead of being re-translated in the browser.

---

## Per-project documentation

- [apps/web/README.md](apps/web/README.md)
- [apps/api/README.md](apps/api/README.md)
- [tools/notion-clone/README.md](tools/notion-clone/README.md)

## For AI agents

[AGENTS.md](AGENTS.md) is the always-loaded context; [CLAUDE.md](CLAUDE.md) and
[.github/copilot-instructions.md](.github/copilot-instructions.md) just point at it.
On-demand skills live in [.claude/skills/](.claude/skills/) — Copilot scans that
directory by default too, so there is nothing tool-specific to configure.
