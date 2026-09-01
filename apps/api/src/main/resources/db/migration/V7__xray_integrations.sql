CREATE TABLE xray_connections (
    id           uuid         PRIMARY KEY,
    workspace_id uuid         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name         varchar(120) NOT NULL,
    base_url     varchar(500) NOT NULL,
    username     varchar(120),
    token_cipher text         NOT NULL,
    created_at   timestamptz  NOT NULL,
    updated_at   timestamptz  NOT NULL,
    version      bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_xray_connections_name UNIQUE (workspace_id, name)
);

CREATE INDEX idx_xray_connections_workspace ON xray_connections (workspace_id, name);

CREATE TABLE xray_project_bindings (
    id                 uuid        PRIMARY KEY,
    workspace_id       uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    project_id         uuid        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    connection_id      uuid        NOT NULL REFERENCES xray_connections (id) ON DELETE RESTRICT,
    remote_project_key varchar(32) NOT NULL,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    version            bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uq_xray_project_bindings_project UNIQUE (project_id)
);

CREATE INDEX idx_xray_project_bindings_connection ON xray_project_bindings (connection_id);
