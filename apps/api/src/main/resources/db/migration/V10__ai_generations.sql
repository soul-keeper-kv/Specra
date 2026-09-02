-- The audit record and the proposal, in one row (01-domain-model.md, 04-database.md). Every model
-- call that could change the repository is written here before anything is applied, with the
-- model, the cost, and — later — the human decision. The first writer is the modelling step; code
-- and fix generations reuse the row shape with a different kind.
CREATE TABLE ai_generations (
    id                uuid         PRIMARY KEY,
    workspace_id      uuid         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    project_id        uuid         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    kind              varchar(16)  NOT NULL,
    status            varchar(16)  NOT NULL,
    -- What the call was about, without a foreign key: the row must stay readable as an audit
    -- line even after its subject is gone.
    subject_type      varchar(32)  NOT NULL,
    subject_id        uuid         NOT NULL,
    -- What it produced, when it produced anything: a test_models row for MODEL.
    result_id         uuid,
    input_checksum    varchar(64)  NOT NULL,
    provider          varchar(64),
    model             varchar(120),
    prompt_tokens     integer,
    completion_tokens integer,
    latency_ms        integer,
    diff              text,
    rationale         text,
    decided_by        uuid,
    decided_at        timestamptz,
    created_at        timestamptz  NOT NULL,
    CONSTRAINT ck_ai_generations_kind CHECK (kind IN ('MODEL', 'CODE', 'FIX')),
    CONSTRAINT ck_ai_generations_status
        CHECK (status IN ('PENDING', 'PROPOSED', 'APPLIED', 'REJECTED', 'SUPERSEDED', 'FAILED'))
);

-- "What did AI do to this test case" is the question an auditor asks, newest first.
CREATE INDEX idx_ai_generations_subject
    ON ai_generations (subject_type, subject_id, created_at DESC);
