# 04 — Database

PostgreSQL with pgvector, Flyway migrations, Hibernate on `ddl-auto: validate`. An entity
without a migration fails startup — that is deliberate and it stays.

## What the database is not allowed to own

**Automation source code.** The repository is the source of truth ([07](07-git.md)). The
database stores, per generated file, the path, a content hash and the commit sha it was last
seen at — enough to detect drift and render a diff, not enough to become a second copy people
start editing.

**Artifacts.** Traces and videos go to object storage. The database stores the key, the
media type, the size and an expiry.

**Secrets in plaintext.** Environment secret values are encrypted at rest with a key from the
environment, and the API never returns a decrypted value — only "set" or "not set". The
runner receives them at job dispatch and never persists them.

## Tables

Ordered by the migration that introduces them. V1–V11 exist; V12 onward is planned.

`V1__init.sql` creates the `vector` extension. It also created the scaffold `notes` tables,
which V4 dropped once `test_cases` replaced them.

**The numbering drifted from the plan, and the numbers on disk win.** Auth was not on the
roadmap and took V3; test management arrived early and took V7–V9. So execution, which this
document once called V7, is V12. When a milestone's bullet and a filename disagree, the
filename is the fact — a migration cannot be renumbered once it has run anywhere.

### V2 — tenancy and projects

| Table               | Notable columns                                                                   |
| ------------------- | --------------------------------------------------------------------------------- |
| `users`             | `email` unique, `display_name`, `password_hash`                                   |
| `workspaces`        | `name`, `slug` unique                                                             |
| `workspace_members` | `(workspace_id, user_id)` unique, `role` — `OWNER` · `ADMIN` · `MEMBER`           |
| `projects`          | `workspace_id`, `key` unique per workspace, `name`, `engine` (`PLAYWRIGHT`)       |
| `git_repositories`  | `project_id` unique, `provider`, `remote_url`, `default_branch`, `credential_id`  |
| `environments`      | `project_id`, `name`, `base_url`, `is_default`                                    |
| `environment_vars`  | `environment_id`, `key`, `value`, `is_secret`, unique on `(environment_id, key)`  |
| `ai_accounts`       | `workspace_id` unique, `provider`, `api_key_cipher`, models, `monthly_budget_usd` |

### V3 — auth

Local sign-in on top of V2's `users`: adds `status`, `locale`, `failed_logins`, `locked_until`
and `last_login_at` to `users`, a case-insensitive unique index on `email`, and
`refresh_tokens` — one row per issued refresh token, hash only, with rotation tracked through
`replaced_by` so a replayed stolen token is detectable rather than merely expired.

### V4 — test cases

Also adds `projects.test_case_sequence`: references like `TC-104` are minted from it under a
row lock, so two authors creating at once cannot share a number.

| Table             | Notable columns                                                                                                                                  |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| `test_cases`      | `project_id`, `reference` (`TC-104`), `title`, `description`, `preconditions`, `expected_result`, `priority`, `automation_status`, `out_of_date` |
| `test_case_steps` | `test_case_id`, `position`, `action_text`, `expected_text`                                                                                       |
| `test_case_tags`  | `(test_case_id, tag)` — same shape as `note_tags`                                                                                                |

### V5 — the model and the generated code

| Table              | Notable columns                                                                                                                     |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------------------- |
| `test_models`      | `test_case_id`, `version` int, `ir_version` int, `document` jsonb, `checksum`, unique `(test_case_id, version)`                     |
| `automation_tests` | `test_case_id` unique, `spec_path`, `test_title`, `current_model_id`, `last_commit_sha`                                             |
| `automation_files` | `automation_test_id`, `path`, `content_hash`, `role` (`SPEC` · `PAGE` · `FIXTURE` · `CONFIG`)                                       |
| `page_objects`     | `project_id`, `name` unique per project, `route`, `inspected_at`                                                                    |
| `page_elements`    | `page_object_id`, `name`, `strategy`, `value`, `fallback_strategy`, `fallback_value`, `confidence`, unique `(page_object_id, name)` |

`test_models.document` is `jsonb` and it is queried — "which test cases touch
`LoginPage.submitButton`" is a GIN-indexed containment query, and it is exactly what impact
analysis needs.

### V6 — git credentials

| Table             | Notable columns                                                         |
| ----------------- | ----------------------------------------------------------------------- |
| `git_credentials` | `workspace_id`, `name` unique per workspace, `username`, `token_cipher` |

Also finishes V2s `git_repositories`: `credential_id` becomes a real `uuid` reference, and
`active_branch` records which branch the projects operations act on right now.

### V7–V9 — test management, and the traceability it needs

Not on the original plan for these numbers: the Xray path arrived early because importing a
real manual case is what makes modelling worth doing, and it took three migrations rather
than one.

| Migration | What it does                                                                                                                                                                                     |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| V7        | `xray_connections` and `xray_project_bindings` — a workspace's Jira/Xray credential, and which external project a Specra project is bound to                                                     |
| V8        | Renames both to `test_management_*` and adds `provider`, `configuration` jsonb and `credentials_cipher`. Xray stopped being the shape and became one value of `provider` behind a port           |
| V9        | `test_cases.external_source/external_id/external_url/imported_at` and `test_case_steps.test_data`, with a partial unique index so one external test imports once per project — re-import updates |

The rename in V8 is the interesting one. V7 modelled Xray directly; V8 turned it into
`TestManagementProvider` with the vendor as data, which is the same containment move as
`GitProvider` and the model providers. Doing it as a rename rather than a new table kept the
rows that already existed.

### V10 — AI audit

| Table            | Notable columns                                                                                                                                                                 |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ai_generations` | `project_id`, `kind` (`MODEL` · `CODE` · `FIX`), `status`, `subject_type/subject_id`, `input_checksum`, `model`, `provider`, `prompt_tokens`, `completion_tokens`, `latency_ms` |

Every model call that could change a repository is a row here **before** anything is applied.
`decided_by`/`decided_at` is the human-in-the-loop principle, written where an auditor reads it.

### V11 — code generations

| Table              | Notable columns                                                                                                                                   |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `code_generations` | `test_case_id`, `test_model_id`, `generation_id` → `ai_generations`, `status`, `adapter_version`, `files` jsonb, `unresolved` jsonb, `commit_sha` |

One proposal per row, with the projected files inline as jsonb — the shape is the adapter's,
so a new file role must not need a migration here. Only one row per case is `PROPOSED` at a
time; generating again supersedes the last, because two live proposals for one file offer a
reviewer two different futures.

This is **not** `automation_tests`, and the difference is worth stating: this table is the
history of proposals, one row per attempt. `automation_tests` (V5) is the current identity of
a test inside the repository — which spec file holds it, under what title, at which commit.
A run targets "this test in that file", which no proposal row can answer. It is still empty;
M6 is what fills it.

### V12 — execution (not yet written)

| Table            | Notable columns                                                                                                                                      |
| ---------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `test_runs`      | `project_id`, `reference` (`RUN-88`), `environment_id`, `commit_sha`, `trigger`, `status`, `queued_at`, `started_at`, `completed_at`, `requested_by` |
| `test_run_items` | `test_run_id`, `automation_test_id`, `browser`, `status`, `duration_ms`, `failed_step_id`, `error_message`, `error_type`                             |
| `test_artifacts` | `test_run_item_id`, `kind` (`SCREENSHOT` · `VIDEO` · `TRACE` · `LOG` · `DOM`), `storage_key`, `size_bytes`, `expires_at`                             |

`failed_step_id` points at an IR step id, not a line number. That is what lets the UI
highlight the manual step the user wrote and the generated line at the same time.

`environments` and `environment_vars` are **not** here: they have existed since V2. M6 needs
entities and a screen for them, not a migration.

## Conventions

- `uuid` primary keys, application-generated, as `notes` already does.
- `created_at` / `updated_at` `timestamptz` not null, `version bigint` for optimistic locking
  on anything a user edits.
- `workspace_id` on every table below `workspaces`, even where a join would reach it.
- Enum-ish columns are `varchar` with a `CHECK` constraint, not a Postgres enum — adding a
  value should not need `ALTER TYPE` in a migration that locks.
- **Never write a migration for `vector_store` or `SPRING_AI_CHAT_MEMORY`.** Spring AI
  creates them, and the vector width follows the active embedding model.
