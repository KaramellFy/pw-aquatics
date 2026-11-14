-- Online payments (docs/DATABASE.md §3.7, Phase 9). Never contains card data: only the
-- provider's references, amounts and statuses. Card numbers, expiry dates and billing
-- details stay with the payment provider.

CREATE TABLE payments (
    id                  uuid PRIMARY KEY,
    order_id            uuid         NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    provider            varchar(20)  NOT NULL CHECK (provider IN ('BANK_TRANSFER', 'STRIPE', 'PAYPAL')),
    provider_reference  varchar(255) UNIQUE,              -- Stripe PaymentIntent id (pi_…)
    status              varchar(20)  NOT NULL CHECK (status IN (
                            'CREATED', 'PENDING', 'SUCCEEDED', 'FAILED', 'CANCELLED',
                            'REFUNDED', 'PARTIALLY_REFUNDED')),
    amount_cents        bigint       NOT NULL CHECK (amount_cents >= 0),
    refunded_cents      bigint       NOT NULL DEFAULT 0 CHECK (refunded_cents >= 0),
    currency            char(3)      NOT NULL DEFAULT 'EUR',
    method_detail       varchar(50),                      -- e.g. 'card' (never card numbers)
    failure_code        varchar(100),                     -- last decline reason, e.g. 'card_declined'
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    version             bigint       NOT NULL DEFAULT 0,
    CHECK (refunded_cents <= amount_cents)
);
CREATE INDEX idx_payments_order ON payments (order_id);
-- One payment per order and provider: a retried card payment reuses the same PaymentIntent.
CREATE UNIQUE INDEX uq_payments_order_provider ON payments (order_id, provider);

-- Every webhook delivery we accepted, so a repeated delivery is processed only once.
-- The payload is reduced to ids, amounts and statuses before it is stored.
CREATE TABLE payment_webhook_events (
    id            uuid PRIMARY KEY,
    provider      varchar(20)  NOT NULL CHECK (provider IN ('STRIPE', 'PAYPAL')),
    event_id      varchar(255) NOT NULL,
    event_type    varchar(100) NOT NULL,
    payload       jsonb        NOT NULL,
    received_at   timestamptz  NOT NULL DEFAULT now(),
    processed_at  timestamptz,
    error         text,
    UNIQUE (provider, event_id)
);
CREATE INDEX idx_payment_webhook_events_received ON payment_webhook_events (received_at);
