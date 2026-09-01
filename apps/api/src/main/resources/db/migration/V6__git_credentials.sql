-- Git credentials, and the columns the git feature needs on V2's git_repositories.
--
-- A credential is its own row rather than a column on the repository, because one token
-- commonly unlocks several repositories and because the GitHub App tokens that replace PATs
-- later attach to an installation, not a repo. The token is ciphertext from SecretsCipher;
-- reads report "set", never the value (07-git.md).

CREATE TABLE git_credentials (
    id           uuid         PRIMARY KEY,
    workspace_id uuid         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name         varchar(120) NOT NULL,
    -- The username the remote pairs with the token; GitHub accepts anything non-empty for
    -- PATs, so this mostly matters for other providers later.
    username     varchar(120),
    token_cipher text         NOT NULL,
    created_at   timestamptz  NOT NULL,
    updated_at   timestamptz  NOT NULL,
    version      bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_git_credentials_name UNIQUE (workspace_id, name)
);

-- V2 declared credential_id as an opaque varchar before the credential store existed. It is
-- a real reference now; the column has never held data anywhere, so the cast is over nothing.
ALTER TABLE git_repositories
    ALTER COLUMN credential_id TYPE uuid USING credential_id::uuid,
    ADD CONSTRAINT fk_git_repositories_credential
        FOREIGN KEY (credential_id) REFERENCES git_credentials (id) ON DELETE SET NULL;

-- Which branch the project's git operations act on right now. Working copies are kept per
-- (project, branch); this is the pointer "checkout" moves. Null means the default branch.
ALTER TABLE git_repositories
    ADD COLUMN active_branch varchar(200);
