ALTER TABLE xray_connections RENAME TO test_management_connections;
ALTER TABLE test_management_connections
    RENAME CONSTRAINT uq_xray_connections_name TO uq_test_management_connections_name;
ALTER INDEX idx_xray_connections_workspace RENAME TO idx_test_management_connections_workspace;

ALTER TABLE test_management_connections
    ADD COLUMN provider varchar(64) NOT NULL DEFAULT 'xray',
    ADD COLUMN configuration jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN credentials_cipher text;

UPDATE test_management_connections
SET configuration = jsonb_build_object(
        'baseUrl', base_url,
        'username', COALESCE(username, '')
    ),
    credentials_cipher = token_cipher;

ALTER TABLE test_management_connections
    ALTER COLUMN provider DROP DEFAULT,
    ALTER COLUMN credentials_cipher SET NOT NULL,
    DROP COLUMN base_url,
    DROP COLUMN username,
    DROP COLUMN token_cipher;

ALTER TABLE xray_project_bindings RENAME TO test_management_bindings;
ALTER TABLE test_management_bindings
    RENAME CONSTRAINT uq_xray_project_bindings_project TO uq_test_management_bindings_project;
ALTER INDEX idx_xray_project_bindings_connection RENAME TO idx_test_management_bindings_connection;
ALTER TABLE test_management_bindings
    RENAME COLUMN remote_project_key TO remote_project_id;
ALTER TABLE test_management_bindings
    ALTER COLUMN remote_project_id TYPE varchar(200);
