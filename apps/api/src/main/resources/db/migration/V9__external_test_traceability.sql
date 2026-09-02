ALTER TABLE test_cases
    ADD COLUMN external_source varchar(64),
    ADD COLUMN external_id varchar(200),
    ADD COLUMN external_url varchar(1000),
    ADD COLUMN imported_at timestamptz;

ALTER TABLE test_case_steps
    ADD COLUMN test_data text;

CREATE UNIQUE INDEX uq_test_cases_external_source
    ON test_cases (project_id, external_source, external_id)
    WHERE external_source IS NOT NULL AND external_id IS NOT NULL;
