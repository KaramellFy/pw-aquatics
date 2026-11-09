-- Baseline: extensions and infrastructure tables (docs/DATABASE.md §3.9).

-- citext: case-insensitive emails and coupon codes; pg_trgm: fuzzy product search.
-- Both are "trusted" extensions, so the database owner can create them.
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ShedLock: keeps scheduled jobs single-run across instances.
CREATE TABLE shedlock (
    name       varchar(64)  PRIMARY KEY,
    lock_until timestamptz  NOT NULL,
    locked_at  timestamptz  NOT NULL,
    locked_by  varchar(255) NOT NULL
);

-- Transactional outbox: written in the same transaction as the business change,
-- delivered by a scheduled worker with retries.
CREATE TABLE outbox_messages (
    id               uuid PRIMARY KEY,
    type             varchar(50)  NOT NULL,
    payload          jsonb        NOT NULL,
    status           varchar(20)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts         int          NOT NULL DEFAULT 0,
    next_attempt_at  timestamptz  NOT NULL DEFAULT now(),
    last_error       text,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    sent_at          timestamptz
);
CREATE INDEX idx_outbox_pending ON outbox_messages (next_attempt_at) WHERE status = 'PENDING';
