-- The IR and the generated code's identity (04-database.md). Tables only for now — the
-- features that write them land with M4/M5 — created here because the roadmap groups the
-- domain schema into M1, and because "which test cases touch LoginPage.submitButton" needs
-- the GIN index to exist before anyone asks it.

-- One row per generated IR version; immutable, so no updated_at and no lock column. The test
-- case points at its current version through automation_tests.current_model_id — history is
-- kept because regeneration and impact analysis both diff against the previous IR.
CREATE TABLE test_models (
    id           uuid        PRIMARY KEY,
    workspace_id uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    test_case_id uuid        NOT NULL REFERENCES test_cases (id) ON DELETE CASCADE,
    -- Which generation of this test case's IR this is.
    version      integer     NOT NULL,
    -- Which version of the test-model schema the document conforms to.
    ir_version   integer     NOT NULL,
    document     jsonb       NOT NULL,
    checksum     varchar(64) NOT NULL,
    created_at   timestamptz NOT NULL,
    CONSTRAINT uq_test_models_version UNIQUE (test_case_id, version)
);

-- Containment queries over the IR: impact analysis asks them, and a GIN over jsonb_path_ops
-- is what makes the answer an index scan.
CREATE INDEX idx_test_models_document ON test_models USING gin (document jsonb_path_ops);

-- The generated artefact's identity in the repository: a path and a title, never the file
-- body — Git is the source of truth for code, the database only detects drift.
CREATE TABLE automation_tests (
    id               uuid         PRIMARY KEY,
    workspace_id     uuid         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    test_case_id     uuid         NOT NULL REFERENCES test_cases (id) ON DELETE CASCADE,
    spec_path        varchar(500) NOT NULL,
    test_title       varchar(300) NOT NULL,
    current_model_id uuid         REFERENCES test_models (id),
    last_commit_sha  varchar(64),
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    version          bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_automation_tests_test_case UNIQUE (test_case_id)
);

CREATE TABLE automation_files (
    id                 uuid         PRIMARY KEY,
    workspace_id       uuid         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    automation_test_id uuid         NOT NULL REFERENCES automation_tests (id) ON DELETE CASCADE,
    path               varchar(500) NOT NULL,
    -- Hash of the content last generated or last seen; enough to detect drift and render a
    -- diff, not enough to become a second copy people start editing.
    content_hash       varchar(64)  NOT NULL,
    role               varchar(16)  NOT NULL,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    version            bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_automation_files_path UNIQUE (automation_test_id, path),
    CONSTRAINT ck_automation_files_role CHECK (role IN ('SPEC', 'PAGE', 'FIXTURE', 'CONFIG'))
);

-- A known page of the application under test. First-class, not a side effect of codegen:
-- several test cases share it, and healing a locator updates one row here instead of every
-- spec that mentions it.
CREATE TABLE page_objects (
    id           uuid         PRIMARY KEY,
    workspace_id uuid         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    project_id   uuid         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    name         varchar(120) NOT NULL,
    route        varchar(500),
    inspected_at timestamptz,
    created_at   timestamptz  NOT NULL,
    updated_at   timestamptz  NOT NULL,
    version      bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_page_objects_name UNIQUE (project_id, name)
);

CREATE TABLE page_elements (
    id                uuid          PRIMARY KEY,
    workspace_id      uuid          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    page_object_id    uuid          NOT NULL REFERENCES page_objects (id) ON DELETE CASCADE,
    name              varchar(120)  NOT NULL,
    strategy          varchar(32)   NOT NULL,
    value             varchar(1000) NOT NULL,
    fallback_strategy varchar(32),
    fallback_value    varchar(1000),
    -- How sure inspection was of this locator, 0–1; the locator planner scores it.
    confidence        numeric(3, 2),
    created_at        timestamptz   NOT NULL,
    updated_at        timestamptz   NOT NULL,
    version           bigint        NOT NULL DEFAULT 0,
    CONSTRAINT uq_page_elements_name UNIQUE (page_object_id, name)
);
