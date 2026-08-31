-- pgvector must exist before Spring AI creates its vector_store table.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE notes (
    id          uuid         PRIMARY KEY,
    title       varchar(200) NOT NULL,
    content     text         NOT NULL,
    indexed_at  timestamptz,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    version     bigint       NOT NULL DEFAULT 0
);

CREATE TABLE note_tags (
    note_id uuid        NOT NULL REFERENCES notes (id) ON DELETE CASCADE,
    tag     varchar(64) NOT NULL,
    CONSTRAINT pk_note_tags PRIMARY KEY (note_id, tag)
);

-- The default listing is "newest first".
CREATE INDEX idx_notes_updated_at ON notes (updated_at DESC);
CREATE INDEX idx_note_tags_tag ON note_tags (tag);
