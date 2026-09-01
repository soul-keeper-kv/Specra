-- Local sign-in on top of the `users` table V2 created.
--
-- V2 gave a user an identity; this migration gives that identity a way to prove itself and a
-- way to be shut out. Everything here is additive, so an installation that already has rows
-- keeps them and every existing user lands as ACTIVE with no failed attempts.

ALTER TABLE users
    ADD COLUMN status        varchar(20) NOT NULL DEFAULT 'ACTIVE',
    -- Which language this user is written to in when there is no request to read it from.
    -- Null means "whatever the request says", which is what a browser already tells us.
    ADD COLUMN locale        varchar(10),
    ADD COLUMN failed_logins int         NOT NULL DEFAULT 0,
    ADD COLUMN locked_until  timestamptz,
    ADD COLUMN last_login_at timestamptz;

ALTER TABLE users
    ADD CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED'));

-- V2's uq_users_email is case-sensitive, so Ann@x.com and ann@x.com would be two accounts and
-- one of them would never be found by a sign-in form that lowercases. The service normalises
-- on write; this index is what makes that true even on the day something forgets to.
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));

-- One row per issued refresh token. Only a hash is stored: a database dump must not also be a
-- set of working sessions. Rotation writes a new row and points the old one at it, so a stolen
-- token replayed after the real client has already refreshed is detectable rather than merely
-- expired — see RefreshTokenService.
CREATE TABLE refresh_tokens (
    id           uuid        PRIMARY KEY,
    user_id      uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash   varchar(64) NOT NULL,
    expires_at   timestamptz NOT NULL,
    revoked_at   timestamptz,
    replaced_by  uuid,
    -- Shown on the "active sessions" screen so a user can recognise the device they are
    -- revoking. Truncated on write; a header is attacker-controlled text.
    user_agent   varchar(256),
    client_ip    varchar(64),
    last_used_at timestamptz,
    created_at   timestamptz NOT NULL,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
