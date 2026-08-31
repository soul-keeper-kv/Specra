# Specra

Project context lives in [`AGENTS.md`](../AGENTS.md) at the repo root — read that first.
This file is only a pointer; it deliberately does not repeat the content so the two
cannot drift apart.

Detailed skills are in `.claude/skills/` (Copilot scans both `.github/skills/` and
`.claude/skills/` by default):

- `specra-api` — Spring Boot, JPA, Flyway, MapStruct, tests
- `specra-web` — Next.js 16, TanStack Table v9, Query, Form
- `specra-ai` — Spring AI, provider selection, pgvector RAG, SSE streaming
- `commit-message` — Conventional Commits matching this repo's commitlint

The six things most likely to be got wrong are listed under **Invariants** in
`AGENTS.md`. Read that section before touching anything in
`apps/api/src/main/java/dev/specra/api/ai/` or `apps/web/src/components/notes/`.
