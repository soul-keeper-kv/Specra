# Specra

One product, two apps that talk to each other:

| Folder | Contents |
| --- | --- |
| [apps/web](apps/web) | Next.js 16 · React 19 · Tailwind 4 · shadcn/ui · lucide · TanStack Query/Table/Form · Zustand · Zod |
| [apps/api](apps/api) | Spring Boot 3.5 · Java 17 · JPA + PostgreSQL · Flyway · Spring AI 1.1 · pgvector RAG · springdoc |
| [tools/notion-clone](tools/notion-clone) | Script that clones an entire Notion workspace to Markdown |

The point of it: **no lock-in to any one LLM vendor**. Anthropic, OpenAI, Ollama and a
locally-run ONNX model all sit on the classpath; picking one is two lines of config.

---

## Quick start

Needs Docker, JDK 17+, Node 20+.

```bash
cp .env.example .env          # add a key if you want a real LLM
npm install                   # root tooling (husky, commitlint, lint-staged)
npm run setup:web             # install the web app's dependencies

npm run db:up                 # Postgres + pgvector (host port 5433)
npm run dev:api               # http://localhost:8080
npm run dev:web               # http://localhost:3000
```

The first API run downloads the ONNX embedding model (~90 MB) into a cache — once only.

| Address | |
| --- | --- |
| <http://localhost:3000> | Web |
| <http://localhost:8080/swagger-ui.html> | API docs |
| <http://localhost:8080/v3/api-docs> | OpenAPI schema |
| <http://localhost:8080/actuator/health> | Health |

---

## Debugging in VS Code

[.vscode/launch.json](.vscode/launch.json) is already set up. Open **Run and Debug** and
pick:

| Configuration | What it does |
| --- | --- |
| **Full stack (API + Web)** | Runs both, bringing Postgres up first |
| **Full stack (throwaway database)** | Same, but the API uses a disposable Testcontainers database |
| API: Spring Boot | API only, with breakpoints in Java |
| API: attach to :5005 | Attaches to an already-running process |
| Web: full stack | Breakpoints in both server and client components in one session |
| Web: Vitest / Playwright | Runs tests from the editor |

The API configuration has a `preLaunchTask` that runs `docker compose up -d --wait`,
which **blocks until Postgres reports healthy** — so the API never starts before the
database. That task also creates `.env` from `.env.example` if it is missing.

Java needs the **Extension Pack for Java**; see
[.vscode/extensions.json](.vscode/extensions.json).

---

## Why Postgres runs on port 5433

Port 5432 is very often already taken by another Postgres on a dev machine. Specra
therefore maps to **5433** so it collides with nothing. To change it, set `DB_PORT` in
`.env` — both `docker-compose.yml` and `application.yml` read that variable.

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
> dropped first: `npm run db:reset`.

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

The three web pages map onto that: **Notes** (CRUD + indexing), **Chat** (streaming,
RAG toggle), **Overview** (status).

---

## Testing

```bash
npm run test:web     # Vitest — 9 tests, including the SSE parser
npm run test:api     # JUnit — 4 unit + 6 integration (Testcontainers with real pgvector)
npm run typecheck
npm run lint
cd apps/web && npm run test:e2e   # Playwright
```

`test:api` needs Docker running: it starts a real `pgvector/pgvector:pg17` container,
runs Flyway, writes and reads vectors, and checks the SSE wire format over real HTTP.

---

## Decisions worth knowing about

**SSE tokens are JSON-encoded.** The SSE spec requires a receiver to strip one space
after `data:`. Model tokens very often begin with a space (`" world"`) and sometimes
contain a newline — plain SSE framing corrupts both. So
[AiController](apps/api/src/main/java/dev/specra/api/ai/AiController.java) sends each
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
