-- A code proposal between "AI produced it" and "a person decided about it".
--
-- The bodies live here and nowhere else, and only until the proposal is applied or rejected:
-- Git is the source of truth for code that exists, but a proposal is precisely code that does
-- not exist yet, so there is no repository to keep it in. Applying writes the files into the
-- working copy and commits them; from that moment automation_files holds the path and the hash,
-- and these rows are history.
CREATE TABLE code_generations (
    id              uuid         PRIMARY KEY,
    workspace_id    uuid         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    project_id      uuid         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    test_case_id    uuid         NOT NULL REFERENCES test_cases (id) ON DELETE CASCADE,
    -- Which IR version this was projected from; a proposal from a stale model is detectable.
    test_model_id   uuid         NOT NULL REFERENCES test_models (id) ON DELETE CASCADE,
    -- The audit row this belongs to (V10), so cost and decision live in one place.
    generation_id   uuid         NOT NULL REFERENCES ai_generations (id) ON DELETE CASCADE,
    status          varchar(16)  NOT NULL,
    adapter_version varchar(32)  NOT NULL,
    -- The projected files, as [{path, role, contents}]. jsonb because the shape is the adapter's
    -- and this table must not need a migration when a file role is added.
    files           jsonb        NOT NULL,
    -- Steps whose page nobody has inspected. A proposal with any of these is reviewable but not
    -- trustworthy, and the UI says so.
    unresolved      jsonb        NOT NULL DEFAULT '[]'::jsonb,
    commit_sha      varchar(64),
    decided_by      uuid         REFERENCES users (id),
    decided_at      timestamptz,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    version         bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ck_code_generations_status
        CHECK (status IN ('PROPOSED', 'APPLIED', 'REJECTED', 'SUPERSEDED'))
);

-- "What is waiting for review on this test case", newest first — the query the UI opens with.
CREATE INDEX idx_code_generations_test_case
    ON code_generations (test_case_id, created_at DESC);
