-- Role 5: what the evidence said about a failure, and what to do about it (08-ai-pipeline.md).
--
-- Analysis is stored, not recomputed. A model call costs money and time, the evidence it read is
-- immutable once a run has finished, and two people opening the same failure must be shown the
-- same reading of it — a second opinion that silently contradicts the first is worse than no
-- second opinion.

CREATE TABLE failure_analyses (
    id               uuid        PRIMARY KEY,
    workspace_id     uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    project_id       uuid        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    -- The matrix cell this reads. One analysis per cell: re-analysing replaces the row rather
    -- than accumulating opinions nobody can choose between.
    test_run_item_id uuid        NOT NULL REFERENCES test_run_items (id) ON DELETE CASCADE,
    test_case_id     uuid        NOT NULL REFERENCES test_cases (id) ON DELETE CASCADE,
    -- The audit row (V10), so cost, provider and latency live in one place for every model call.
    generation_id    uuid        NOT NULL REFERENCES ai_generations (id) ON DELETE CASCADE,
    -- The classification from the table in 08-ai-pipeline.md. PRODUCT_BUG is the row that makes
    -- the feature honest: it is the answer that proposes no diff, and a tool that cannot reach it
    -- will eventually "fix" a real regression by loosening the assertion that caught it.
    root_cause       varchar(32) NOT NULL,
    -- 0..100. Low confidence is shown, not hidden: a guess presented as a finding is the failure
    -- mode this whole feature has to avoid.
    confidence       integer     NOT NULL,
    -- What the model concluded, in the reader's language-neutral words. Prose, deliberately: the
    -- summary is for a person deciding whether to trust the proposal, not for a machine.
    summary          text        NOT NULL,
    -- Why it concluded that, tied to the evidence it read.
    rationale        text        NOT NULL,
    -- What it suggests doing, in words, before any code exists. A FIX generation is a separate
    -- decision on a separate row; this column is what a reader judges before asking for one.
    suggestion       text,
    -- The evidence actually put in front of the model, as {errorMessage, errorType, failedStepId,
    -- specExcerpt, …}. Kept so an analysis can be audited against its inputs rather than against
    -- a reconstruction of them, which is what makes "why did it say that" answerable at all.
    evidence         jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL,
    version          bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uq_failure_analyses_item UNIQUE (test_run_item_id),
    CONSTRAINT ck_failure_analyses_confidence CHECK (confidence BETWEEN 0 AND 100),
    CONSTRAINT ck_failure_analyses_root_cause
        CHECK (root_cause IN ('LOCATOR_DRIFT', 'TIMING', 'APPLICATION_CHANGED',
                              'TEST_DATA', 'PRODUCT_BUG', 'UNKNOWN'))
);

CREATE INDEX idx_failure_analyses_case ON failure_analyses (test_case_id, created_at DESC);

-- A repair proposal is a code proposal, so it lives in code_generations rather than in a second
-- table with its own propose/apply/reject flow. There is exactly one path that writes into a
-- user's repository, and adding a second one is how "a human approves every write" quietly stops
-- being true.
ALTER TABLE code_generations
    ADD COLUMN kind varchar(16) NOT NULL DEFAULT 'CODE',
    -- Which failure this repairs. Null for an ordinary CODE generation.
    ADD COLUMN failure_analysis_id uuid REFERENCES failure_analyses (id) ON DELETE SET NULL;

ALTER TABLE code_generations
    ADD CONSTRAINT ck_code_generations_kind CHECK (kind IN ('CODE', 'FIX'));

-- A FIX repairs code that already exists, so it is not a projection of any IR version — it is a
-- patch to a spec file, produced from evidence. The column stays NOT NULL for CODE, which is
-- where "which IR was this projected from" is a real question.
ALTER TABLE code_generations
    ALTER COLUMN test_model_id DROP NOT NULL;

ALTER TABLE code_generations
    ADD CONSTRAINT ck_code_generations_model_required
        CHECK (kind <> 'CODE' OR test_model_id IS NOT NULL);
