# Specra

Project context lives in [`AGENTS.md`](../AGENTS.md) at the repo root — read that first.
This file is only a pointer; it deliberately does not repeat the content so the two
cannot drift apart.

Detailed skills are in `.claude/skills/` (Copilot scans both `.github/skills/` and
`.claude/skills/` by default):

- `specra-feature` — **start here** for anything user-visible: the end-to-end checklist and
  the decisions that are already settled
- `specra-api` — Spring Boot, JPA, Flyway, MapStruct, RFC 9457 errors, i18n, tracing, tests
- `specra-web` — Next.js 16, next-intl, theming, TanStack Table v9, Query, Form
- `specra-ai` — Spring AI, provider selection, pgvector RAG, SSE streaming
- `commit-message` — Conventional Commits matching this repo's commitlint

The thirteen things most likely to be got wrong are listed under **Invariants** in
`AGENTS.md`. Read that section before touching anything in
`apps/api/src/main/java/dev/specra/api/feature/ai/` (web/ · service/ · tool/ · dto/) or
`apps/web/src/features/notes/`.
