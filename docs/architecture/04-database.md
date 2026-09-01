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

Ordered by the migration that introduces them. `V1__init.sql` already exists and creates the
`vector` extension plus the scaffold `notes` tables.

### V2 — tenancy and projects

| Table               | Notable columns                                                                  |
| ------------------- | -------------------------------------------------------------------------------- |
| `users`             | `email` unique, `display_name`, `password_hash`                                  |
| `workspaces`        | `name`, `slug` unique                                                            |
| `workspace_members` | `(workspace_id, user_id)` unique, `role` — `OWNER` · `ADMIN` · `MEMBER`          |
| `projects`          | `workspace_id`, `key` unique per workspace, `name`, `engine` (`PLAYWRIGHT`)      |
| `git_repositories`  | `project_id` unique, `provider`, `remote_url`, `default_branch`, `credential_id` |
| `environments`      | `project_id`, `name`, `base_url`, `is_default`                                   |
| `environment_vars`  | `environment_id`, `key`, `value`, `is_secret`, unique on `(environment_id, key)` |

### V3 — test cases

| Table             | Notable columns                                                                                                                                  |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| `test_cases`      | `project_id`, `reference` (`TC-104`), `title`, `description`, `preconditions`, `expected_result`, `priority`, `automation_status`, `out_of_date` |
| `test_case_steps` | `test_case_id`, `position`, `action_text`, `expected_text`                                                                                       |
| `test_case_tags`  | `(test_case_id, tag)` — same shape as `note_tags`                                                                                                |

### V4 — the model and the generated code

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

### V5 — execution

| Table            | Notable columns                                                                                                                                      |
| ---------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `test_runs`      | `project_id`, `reference` (`RUN-88`), `environment_id`, `commit_sha`, `trigger`, `status`, `queued_at`, `started_at`, `completed_at`, `requested_by` |
| `test_run_items` | `test_run_id`, `automation_test_id`, `browser`, `status`, `duration_ms`, `failed_step_id`, `error_message`, `error_type`                             |
| `test_artifacts` | `test_run_item_id`, `kind` (`SCREENSHOT` · `VIDEO` · `TRACE` · `LOG` · `DOM`), `storage_key`, `size_bytes`, `expires_at`                             |

`failed_step_id` points at an IR step id, not a line number. That is what lets the UI
highlight the manual step the user wrote and the generated line at the same time.

### V6 — AI audit

| Table            | Notable columns                                                                                                                                                                                                                  |
| ---------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ai_generations` | `project_id`, `kind` (`MODEL` · `CODE` · `FIX`), `status`, `subject_type/subject_id`, `input_checksum`, `model`, `provider`, `prompt_tokens`, `completion_tokens`, `latency_ms`, `diff`, `rationale`, `decided_by`, `decided_at` |

Every model call that could change a repository is a row here **before** anything is applied.
`diff` is the proposal; `decided_by`/`decided_at` is the human-in-the-loop principle, written
down where an auditor can read it.

## Conventions

- `uuid` primary keys, application-generated, as `notes` already does.
- `created_at` / `updated_at` `timestamptz` not null, `version bigint` for optimistic locking
  on anything a user edits.
- `workspace_id` on every table below `workspaces`, even where a join would reach it.
- Enum-ish columns are `varchar` with a `CHECK` constraint, not a Postgres enum — adding a
  value should not need `ALTER TYPE` in a migration that locks.
- **Never write a migration for `vector_store` or `SPRING_AI_CHAT_MEMORY`.** Spring AI
  creates them, and the vector width follows the active embedding model.
