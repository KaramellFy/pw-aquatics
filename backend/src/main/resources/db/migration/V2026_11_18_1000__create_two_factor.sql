-- Two-step sign-in for administrators (docs/adr/015-two-step-sign-in-for-administrators.md).

-- The key shared with the authenticator app. Encrypted with APP_ENCRYPTION_KEY (AES-256-GCM,
-- bound to the user id); a copy of the database alone does not reveal it.
CREATE TABLE user_two_factor (
    id                         uuid PRIMARY KEY,
    user_id                    uuid        NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    -- The key in use; null until the first code from it was entered.
    secret_ciphertext          text,
    -- A new key shown during setup, waiting for its first code (replaces nothing until then).
    pending_secret_ciphertext  text,
    enabled_at                 timestamptz,
    -- The 30-second step of the last accepted code: each code is accepted once only.
    last_used_step             bigint      NOT NULL DEFAULT 0,
    created_at                 timestamptz NOT NULL DEFAULT now(),
    updated_at                 timestamptz NOT NULL DEFAULT now(),
    version                    bigint      NOT NULL DEFAULT 0,
    CHECK ((secret_ciphertext IS NULL) = (enabled_at IS NULL))
);

-- One-time codes for when the phone is lost; only SHA-256 hashes are stored.
CREATE TABLE two_factor_recovery_codes (
    id          uuid PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES user_two_factor(user_id) ON DELETE CASCADE,
    code_hash   char(64)    NOT NULL,
    used_at     timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    -- Also serves as the index on user_id.
    UNIQUE (user_id, code_hash)
);

-- Administrators signed in before two-step sign-in existed are signed out once, so their next
-- sign-in asks for the code (or for setting it up) before the admin area opens.
DELETE FROM spring_session
WHERE principal_name IN (SELECT id::text FROM users WHERE role = 'ADMIN');
