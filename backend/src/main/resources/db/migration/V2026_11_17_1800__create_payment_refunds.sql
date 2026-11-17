-- Refunds the shop starts from the admin order page (docs/API.md §3.5). Refunds made in the
-- provider's own dashboard are only counted in payments.refunded_cents (from the webhook).
CREATE TABLE payment_refunds (
    id                  uuid PRIMARY KEY,
    payment_id          uuid         NOT NULL REFERENCES payments(id) ON DELETE RESTRICT,
    -- From the admin's request: a repeated request with the same key refunds nothing twice. Also
    -- passed to Stripe / PayPal as their idempotency key.
    idempotency_key     varchar(100) NOT NULL UNIQUE,
    amount_cents        bigint       NOT NULL CHECK (amount_cents > 0),
    currency            char(3)      NOT NULL,
    status              varchar(20)  NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    provider_reference  varchar(100) UNIQUE,       -- Stripe re_…, PayPal refund id
    failure_code        varchar(100),
    reason              varchar(500),              -- internal, written by the shop owner
    requested_by        uuid         REFERENCES users(id) ON DELETE SET NULL,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    version             bigint       NOT NULL DEFAULT 0
);
CREATE INDEX idx_payment_refunds_payment      ON payment_refunds (payment_id);
CREATE INDEX idx_payment_refunds_requested_by ON payment_refunds (requested_by);
