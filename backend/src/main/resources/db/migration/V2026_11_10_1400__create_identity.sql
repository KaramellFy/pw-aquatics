-- Identity, customer addresses, sessions and audit log (docs/DATABASE.md §3.1, §3.9).

CREATE TABLE users (
    id                    uuid PRIMARY KEY,
    -- Always stored lower-case by the application; citext keeps uniqueness case-insensitive regardless.
    email                 citext       NOT NULL UNIQUE,
    -- NULL only for anonymised accounts.
    password_hash         varchar(255),
    first_name            varchar(100) NOT NULL,
    last_name             varchar(100) NOT NULL,
    phone                 varchar(40),
    -- Language for emails sent to this user ('en' or 'de').
    locale                varchar(5)   NOT NULL DEFAULT 'en',
    role                  varchar(20)  NOT NULL DEFAULT 'CUSTOMER' CHECK (role IN ('CUSTOMER', 'ADMIN')),
    status                varchar(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'LOCKED', 'ANONYMISED')),
    email_verified_at     timestamptz,
    failed_login_attempts int          NOT NULL DEFAULT 0,
    locked_until          timestamptz,
    last_login_at         timestamptz,
    marketing_opt_in      boolean      NOT NULL DEFAULT false,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    version               bigint       NOT NULL DEFAULT 0,
    CHECK (password_hash IS NOT NULL OR status = 'ANONYMISED')
);

-- One-time tokens. Only a SHA-256 hash is stored; the raw token exists only in the email link.
CREATE TABLE user_tokens (
    id          uuid PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        varchar(30) NOT NULL CHECK (type IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'EMAIL_CHANGE')),
    token_hash  char(64)    NOT NULL UNIQUE,
    -- The address to switch to; only for EMAIL_CHANGE.
    new_email   citext,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CHECK ((type = 'EMAIL_CHANGE') = (new_email IS NOT NULL))
);
CREATE INDEX idx_user_tokens_user ON user_tokens (user_id);

CREATE TABLE addresses (
    id                  uuid PRIMARY KEY,
    user_id             uuid         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label               varchar(50),
    first_name          varchar(100) NOT NULL,
    last_name           varchar(100) NOT NULL,
    company             varchar(150),
    street              varchar(150) NOT NULL,
    house_number        varchar(20)  NOT NULL,
    address_line2       varchar(150),
    postal_code         varchar(20)  NOT NULL,
    city                varchar(100) NOT NULL,
    country_code        char(2)      NOT NULL CHECK (country_code ~ '^[A-Z]{2}$'),
    phone               varchar(40),
    is_default_shipping boolean      NOT NULL DEFAULT false,
    is_default_billing  boolean      NOT NULL DEFAULT false,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_addresses_user ON addresses (user_id);
CREATE UNIQUE INDEX uq_addresses_default_shipping ON addresses (user_id) WHERE is_default_shipping;
CREATE UNIQUE INDEX uq_addresses_default_billing  ON addresses (user_id) WHERE is_default_billing;

-- Audit trail of security-relevant and (later) admin actions. Never contains passwords or tokens.
CREATE TABLE audit_log (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_user_id uuid         REFERENCES users(id) ON DELETE SET NULL,
    action        varchar(100) NOT NULL,
    entity_type   varchar(50),
    entity_id     uuid,
    details       jsonb,
    ip_address    inet,
    created_at    timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_log_entity  ON audit_log (entity_type, entity_id);
CREATE INDEX idx_audit_log_created ON audit_log (created_at DESC);
CREATE INDEX idx_audit_log_actor   ON audit_log (actor_user_id);

-- The ledger column from Phase 2 can now reference users.
ALTER TABLE stock_movements
    ADD CONSTRAINT fk_stock_movements_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;

-- Spring Session JDBC (official PostgreSQL schema of spring-session-jdbc 4.1).
CREATE TABLE spring_session (
    primary_id            char(36) NOT NULL,
    session_id            char(36) NOT NULL,
    creation_time         bigint   NOT NULL,
    last_access_time      bigint   NOT NULL,
    max_inactive_interval int      NOT NULL,
    expiry_time           bigint   NOT NULL,
    principal_name        varchar(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);
CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id char(36)     NOT NULL,
    attribute_name     varchar(200) NOT NULL,
    attribute_bytes    bytea        NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session (primary_id) ON DELETE CASCADE
);
