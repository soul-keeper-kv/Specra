-- Execution: one run, its matrix cells, and the evidence each cell produced (06-execution.md).
--
-- The same conventions as everything below a workspace: uuid keys, workspace_id on every table
-- even where a join would reach it, and enum-ish columns as varchar + CHECK so adding a value
-- is not an ALTER TYPE that locks.

-- RUN-88, minted from the project row under a lock, exactly like TC-n. Users read these out
-- loud and paste them into Jira, so a gap after a rollback is fine and a duplicate is not.
ALTER TABLE projects
    ADD COLUMN test_run_sequence integer NOT NULL DEFAULT 0;

-- A run is created against a COMMIT SHA, not against "the working copy as it is now". Two
-- people opening the same run must see the same code, and a result that cannot be tied to an
-- exact tree is not evidence. Running uncommitted edits is allowed, but then the sha is the
-- base and the diff is stored beside it, so the run is still reproducible.
CREATE TABLE test_runs (
    id             uuid        PRIMARY KEY,
    workspace_id   uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    project_id     uuid        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    reference      varchar(32) NOT NULL,
    environment_id uuid        NOT NULL REFERENCES environments (id),
    commit_sha     varchar(64) NOT NULL,
    -- The uncommitted changes this run was started with, if any. Null is the ordinary case.
    dirty_diff     text,
    trigger        varchar(16) NOT NULL,
    status         varchar(16) NOT NULL,
    -- Null until the runner picks it up / finishes; the three together are the timeline.
    queued_at      timestamptz NOT NULL,
    started_at     timestamptz,
    completed_at   timestamptz,
    -- Who asked. Kept when the account is deleted: an audit trail that disappears is not one.
    requested_by   uuid        REFERENCES users (id) ON DELETE SET NULL,
    -- Why it stopped, when it stopped for a reason that is ours rather than the test's.
    error_message  text,
    created_at     timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL,
    version        bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uq_test_runs_reference UNIQUE (project_id, reference),
    CONSTRAINT ck_test_runs_trigger CHECK (trigger IN ('MANUAL', 'SCHEDULE', 'CI')),
    CONSTRAINT ck_test_runs_status
        CHECK (status IN ('QUEUED', 'RUNNING', 'PASSED', 'FAILED', 'ERROR', 'CANCELLED'))
);

CREATE INDEX idx_test_runs_project ON test_runs (project_id, queued_at DESC);

-- One row per matrix cell: test × browser, over the run's one environment.
--
-- FAILED and ERROR are deliberately different states. FAILED means the test ran and an
-- assertion or a locator did not hold — that is worth an AI failure analysis. ERROR means the
-- run could not complete (install, build, timeout, infrastructure), which is usually ours to
-- fix and never worth asking a model about.
CREATE TABLE test_run_items (
    id                 uuid        PRIMARY KEY,
    workspace_id       uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    test_run_id        uuid        NOT NULL REFERENCES test_runs (id) ON DELETE CASCADE,
    -- What was executed. Nullable so a run survives the automation test being deleted; the
    -- test case id below is what the UI actually groups and links by.
    automation_test_id uuid        REFERENCES automation_tests (id) ON DELETE SET NULL,
    test_case_id       uuid        NOT NULL REFERENCES test_cases (id) ON DELETE CASCADE,
    browser            varchar(32) NOT NULL,
    status             varchar(16) NOT NULL,
    duration_ms        integer,
    -- An IR step id, not a line number. That is what lets the UI highlight the manual step the
    -- user wrote and the generated line at the same time.
    failed_step_id     varchar(64),
    error_message      text,
    error_type         varchar(64),
    started_at         timestamptz,
    completed_at       timestamptz,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    version            bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uq_test_run_items_cell UNIQUE (test_run_id, test_case_id, browser),
    CONSTRAINT ck_test_run_items_browser CHECK (browser IN ('chromium', 'firefox', 'webkit')),
    CONSTRAINT ck_test_run_items_status
        CHECK (status IN ('QUEUED', 'RUNNING', 'PASSED', 'FAILED', 'ERROR', 'SKIPPED'))
);

CREATE INDEX idx_test_run_items_run ON test_run_items (test_run_id);
CREATE INDEX idx_test_run_items_case ON test_run_items (test_case_id, created_at DESC);

-- The evidence, by reference only. Bytes live in object storage and are served through
-- short-lived signed URLs: the API never proxies them and Postgres never holds them.
-- expires_at is not decoration — traces are large and a QA team runs a lot of tests.
CREATE TABLE test_artifacts (
    id               uuid         PRIMARY KEY,
    workspace_id     uuid         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    test_run_item_id uuid         NOT NULL REFERENCES test_run_items (id) ON DELETE CASCADE,
    kind             varchar(16)  NOT NULL,
    storage_key      varchar(500) NOT NULL,
    content_type     varchar(120),
    size_bytes       bigint,
    expires_at       timestamptz,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    version          bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_test_artifacts_key UNIQUE (storage_key),
    CONSTRAINT ck_test_artifacts_kind
        CHECK (kind IN ('SCREENSHOT', 'VIDEO', 'TRACE', 'LOG', 'DOM'))
);

CREATE INDEX idx_test_artifacts_item ON test_artifacts (test_run_item_id);
