-- Test cases: the manual case, its ordered steps and its tags (04-database.md).
--
-- Same conventions as V2: application-generated uuid keys, workspace_id on every table even
-- where a join would reach it, enum-ish columns as varchar + CHECK.

-- References like TC-104 are a per-project sequence, separate from the uuid, because users
-- read them out loud and paste them into Jira. The counter lives on the project row and is
-- bumped under a row lock, so two people creating at once cannot mint the same number.
ALTER TABLE projects
    ADD COLUMN test_case_sequence integer NOT NULL DEFAULT 0;

CREATE TABLE test_cases (
    id                uuid         PRIMARY KEY,
    workspace_id      uuid         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    project_id        uuid         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    reference         varchar(32)  NOT NULL,
    title             varchar(200) NOT NULL,
    description       text,
    preconditions     text,
    expected_result   text,
    priority          varchar(16)  NOT NULL,
    automation_status varchar(24)  NOT NULL,
    -- A flag, not a state: set when the case is edited after its IR was generated, cleared
    -- by regeneration (01-domain-model.md).
    out_of_date       boolean      NOT NULL DEFAULT false,
    -- Set once the case's text has been embedded into the pgvector store, as notes did.
    indexed_at        timestamptz,
    created_at        timestamptz  NOT NULL,
    updated_at        timestamptz  NOT NULL,
    version           bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_test_cases_reference UNIQUE (project_id, reference),
    CONSTRAINT ck_test_cases_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_test_cases_automation_status
        CHECK (automation_status IN ('NOT_AUTOMATED', 'MODELLED', 'GENERATED', 'COMMITTED'))
);

CREATE INDEX idx_test_cases_project ON test_cases (project_id, updated_at DESC);

CREATE TABLE test_case_steps (
    id            uuid    PRIMARY KEY,
    workspace_id  uuid    NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    test_case_id  uuid    NOT NULL REFERENCES test_cases (id) ON DELETE CASCADE,
    position      integer NOT NULL,
    action_text   text    NOT NULL,
    expected_text text,
    -- Deferred so an edit can rewrite the whole list in one transaction without the interim
    -- states (two rows momentarily on one position) failing a check nobody will ever see.
    CONSTRAINT uq_test_case_steps_position UNIQUE (test_case_id, position)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE test_case_tags (
    test_case_id uuid        NOT NULL REFERENCES test_cases (id) ON DELETE CASCADE,
    tag          varchar(64) NOT NULL,
    CONSTRAINT pk_test_case_tags PRIMARY KEY (test_case_id, tag)
);

CREATE INDEX idx_test_case_tags_tag ON test_case_tags (tag);
