-- Sign in with Google (docs/adr/014-sign-in-with-google.md).

-- Accounts created through Google have no password until the customer sets one (through the
-- "forgot password" email), so the password is no longer required.
ALTER TABLE users DROP CONSTRAINT users_check;

-- A Google account linked to a shop account, identified by Google's stable subject id ("sub"),
-- never by the email address (which can change at Google).
CREATE TABLE user_identities (
    id            uuid PRIMARY KEY,
    user_id       uuid         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider      varchar(20)  NOT NULL CHECK (provider IN ('GOOGLE')),
    subject       varchar(255) NOT NULL,
    -- The Google account's address at the last sign-in, shown on the account page.
    email         varchar(254) NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    last_used_at  timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (provider, subject),
    -- One Google account per shop account; also serves as the index on user_id.
    UNIQUE (user_id, provider)
);
