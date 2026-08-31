---
name: commit-message
description: Write commit messages for the Specra repo following Conventional Commits, matching the commitlint that runs in the commit-msg hook. Use when committing, rewording a commit, drafting a pull request description, or when a commit was rejected by the hook and you need to know which rule fired.
---

# Commit messages — Specra

The `.husky/commit-msg` hook runs `pnpm exec commitlint --edit "$1"`, configured with
`@commitlint/config-conventional` plus this repo's own `scope-enum`
(`commitlint.config.mjs`).

Every rule below was verified by running messages through the actual commitlint, not
recalled from the spec.

## Shape

```
<type>(<scope>): <subject>

<body — why, not what>

<footer — BREAKING CHANGE, refs, trailers>
```

The scope is optional. `feat: ...` is valid.

## What blocks a commit, and what only warns

This is the most important distinction: commitlint does not run with `--strict`, so
warnings still commit.

| Violation                                          | Result                        |
| -------------------------------------------------- | ----------------------------- |
| `type` outside the list (`feature`, `update`, …)   | **blocked**                   |
| `subject` starting with a capital — `Add endpoint` | **blocked**                   |
| `subject` in Title Case — `Add New Endpoint`       | **blocked**                   |
| `subject` ending in `.`                            | **blocked**                   |
| Empty `subject`, or a missing `type:`              | **blocked**                   |
| Header longer than 100 characters                  | **blocked**                   |
| Any body or footer line longer than 100 characters | **blocked**                   |
| `scope` outside the enum — `feat(backend):`        | warning only, commit succeeds |
| Body not separated from the header by a blank line | warning only                  |

Scope being a warning is intentional: the enum mirrors the folders that exist today, and
adding a new folder should not jam a commit.

## `type` — exactly 11 values

| type       | Use for                                         |
| ---------- | ----------------------------------------------- |
| `feat`     | A capability a user can see                     |
| `fix`      | A bug fix                                       |
| `refactor` | Structural change, behaviour unchanged          |
| `perf`     | Performance improvement                         |
| `test`     | Tests only                                      |
| `docs`     | Documentation only — README, SKILL.md, comments |
| `style`    | Formatting, whitespace — no logic change        |
| `build`    | `pom.xml`, `package.json`, Dockerfile, compose  |
| `ci`       | Workflows, hooks, `.vscode/`                    |
| `chore`    | Housekeeping that fits nowhere above            |
| `revert`   | Reverting an earlier commit                     |

There is no `feature`, `update`, `change`, or `improve` — commitlint rejects them
outright.

## `scope` — the 6 values in the enum

| scope   | Covers                                               |
| ------- | ---------------------------------------------------- |
| `api`   | `apps/api/` — Java, pom, application.yml, migrations |
| `web`   | `apps/web/` — TSX, config, tests                     |
| `tools` | `tools/`                                             |
| `ci`    | Hooks, workflows, `.vscode/`                         |
| `deps`  | Dependency bumps                                     |
| `docs`  | README, AGENTS.md, `.claude/skills/`                 |

When a change spans both apps, drop the scope:
`refactor: rename Note to Document across web and api`.

## `subject`

Required: **start lowercase**, no trailing period, whole header ≤ 100 characters.

Use the imperative, and describe the _outcome_ rather than the action taken.

```
✓ feat(api): add pgvector similarity search endpoint
✓ fix(web): keep leading space in streamed SSE tokens
✓ refactor(api): split RagService out of NoteService
✓ feat(api): add OpenAPI codegen          ← a mid-sentence acronym is fine
✓ build(deps): bump spring ai to 1.1.8

✗ feat(api): Add endpoint                 ← blocked: leading capital
✗ feat(api): add endpoint.                ← blocked: trailing period
✗ feature(api): add endpoint              ← blocked: invalid type
✗ fix(web): fixed bug                     ← passes lint but says nothing: which bug?
✗ chore: update files                     ← passes lint but says nothing
```

Capitalised acronyms in the middle (`OpenAPI`, `SSE`, `JPA`) are **not** blocked — only
the first word is checked.

## `body` — where the _why_ goes

The diff already says what the code does. The body carries what the diff cannot: the
reason, the constraint, the option that was rejected.

Separate it from the header with a blank line; keep every line ≤ 100 characters.

```
fix(web): keep leading space in streamed SSE tokens

The SSE spec makes a receiver strip one space after `data:`, and model tokens
very often begin with a space, so "Hello world" arrived as "Helloworld".

Fix: the API JSON-encodes each token and the client JSON.parses it. That also
covers tokens containing a newline.

Refs: AiStreamIT
```

A one-line commit is enough when the change speaks for itself
(`docs: fix typo in README`). A body is needed when someone will later ask "why was it
done this way?".

## Breaking changes

Two forms, both accepted:

```
feat(api)!: drop PageResponse envelope
```

```
feat(api): drop PageResponse envelope

BREAKING CHANGE: the web app must read a bare array instead of a `content` field.
```

## Trailers

Allowed, as long as they follow a blank line and each line is ≤ 100 characters:

```
Co-Authored-By: Name <email@example.com>
Refs: #123
```

## Checking before you commit

```bash
echo "feat(api): add endpoint" | pnpm exec commitlint
```

No output means it passed. Read the output carefully for warnings — they still commit,
but usually indicate a mis-chosen scope.

## What the hooks do

| Hook         | Command                                | Effect                                                            |
| ------------ | -------------------------------------- | ----------------------------------------------------------------- |
| `pre-commit` | `pnpm exec lint-staged` (root)         | Prettier over staged `*.md`, `*.json`, `*.yml` outside `apps/web` |
| `pre-commit` | `cd apps/web && pnpm exec lint-staged` | Prettier + ESLint `--fix` over staged web sources                 |
| `commit-msg` | `pnpm exec commitlint --edit`          | Rejects the message if any error-level rule fires                 |

Because `lint-staged` rewrites files, a commit can pick up formatting changes you did
not make by hand. That is expected, not a fault.

Java is **not** in the hooks: `spotless:check` runs in Maven's `validate` phase, so any
`./mvnw` command already fails on unformatted code. Fix it with `./mvnw spotless:apply`
(or `pnpm format:api` from the repo root).

## Unrelated changes

Split them into separate commits rather than bundling them under
`chore: various fixes`. One commit should answer exactly one "why".
