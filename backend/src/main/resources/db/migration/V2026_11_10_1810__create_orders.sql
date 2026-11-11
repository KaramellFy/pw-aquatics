-- Phase 5: orders (docs/DATABASE.md §3.7). Everything a customer bought is stored as a
-- snapshot, so later changes to products, prices or addresses never alter an order.

-- Order numbers look like 'PW-2027-000123'. Gaps are fine (invoice numbers are separate).
CREATE SEQUENCE order_number_seq;

CREATE TABLE orders (
    id                    uuid PRIMARY KEY,
    order_number          varchar(20)  NOT NULL UNIQUE,
    user_id               uuid         REFERENCES users(id) ON DELETE SET NULL,
    customer_email        citext       NOT NULL,
    customer_locale       varchar(5)   NOT NULL DEFAULT 'en',
    status                varchar(20)  NOT NULL CHECK (status IN (
                              'PENDING_PAYMENT', 'PAID', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    payment_status        varchar(20)  NOT NULL DEFAULT 'UNPAID' CHECK (payment_status IN (
                              'UNPAID', 'PAID', 'PARTIALLY_REFUNDED', 'REFUNDED', 'FAILED')),
    payment_method        varchar(30)  NOT NULL CHECK (payment_method IN (
                              'BANK_TRANSFER', 'STRIPE', 'PAYPAL')),
    currency              char(3)      NOT NULL DEFAULT 'EUR',
    -- All amounts gross (incl. VAT), in cents, calculated by the server.
    subtotal_cents        bigint       NOT NULL CHECK (subtotal_cents >= 0),
    discount_cents        bigint       NOT NULL DEFAULT 0 CHECK (discount_cents >= 0),
    shipping_cents        bigint       NOT NULL CHECK (shipping_cents >= 0),
    total_cents           bigint       NOT NULL CHECK (total_cents >= 0),
    tax_cents             bigint       NOT NULL CHECK (tax_cents >= 0),   -- VAT contained in total
    coupon_code           varchar(64),                                    -- snapshot (Phase 8)
    shipping_method_code  varchar(50)  NOT NULL,                          -- snapshot
    shipping_method_name  varchar(100) NOT NULL,                          -- snapshot
    shipping_carrier      varchar(50),                                    -- snapshot
    planned_dispatch_date date,                                           -- earliest dispatch day
    customer_note         varchar(1000),
    -- Proof that the customer accepted the terms (and live-animal conditions) when ordering.
    terms_accepted_at     timestamptz  NOT NULL,
    idempotency_key       varchar(100),
    placed_at             timestamptz  NOT NULL,
    payment_due_at        timestamptz,                                    -- unpaid orders expire then
    paid_at               timestamptz,
    cancelled_at          timestamptz,
    cancellation_reason   varchar(500),
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    version               bigint       NOT NULL DEFAULT 0,
    CHECK (total_cents = subtotal_cents - discount_cents + shipping_cents),
    UNIQUE (user_id, idempotency_key)
);
CREATE INDEX idx_orders_user_placed   ON orders (user_id, placed_at DESC);
CREATE INDEX idx_orders_status_placed ON orders (status, placed_at DESC);
CREATE INDEX idx_orders_placed        ON orders (placed_at);
CREATE INDEX idx_orders_payment_due   ON orders (payment_due_at) WHERE status = 'PENDING_PAYMENT';

CREATE TABLE order_items (
    id                uuid PRIMARY KEY,
    order_id          uuid         NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id        uuid         REFERENCES products(id)         ON DELETE SET NULL,
    variant_id        uuid         REFERENCES product_variants(id) ON DELETE SET NULL,
    position          int          NOT NULL,
    -- Snapshot at the time of purchase
    sku               varchar(64)  NOT NULL,
    product_name      varchar(150) NOT NULL,
    variant_name      varchar(100) NOT NULL,
    species_name      varchar(150),
    grade_name        varchar(60),
    is_live_animal    boolean      NOT NULL,
    pack_size         int          NOT NULL CHECK (pack_size > 0),
    unit_price_cents  bigint       NOT NULL CHECK (unit_price_cents >= 0),
    quantity          int          NOT NULL CHECK (quantity > 0),
    discount_cents    bigint       NOT NULL DEFAULT 0 CHECK (discount_cents >= 0),   -- share of the order discount
    line_total_cents  bigint       NOT NULL CHECK (line_total_cents >= 0),           -- unit price × quantity − discount
    tax_rate_bps      int          NOT NULL CHECK (tax_rate_bps BETWEEN 0 AND 10000),
    tax_cents         bigint       NOT NULL CHECK (tax_cents >= 0),                  -- VAT contained in line_total
    CHECK (line_total_cents = unit_price_cents * quantity - discount_cents)
);
CREATE INDEX idx_order_items_order   ON order_items (order_id, position);
CREATE INDEX idx_order_items_product ON order_items (product_id);
CREATE INDEX idx_order_items_variant ON order_items (variant_id);

-- Address snapshots (never a reference to the editable address book).
CREATE TABLE order_addresses (
    order_id      uuid         NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    type          varchar(10)  NOT NULL CHECK (type IN ('SHIPPING', 'BILLING')),
    first_name    varchar(100) NOT NULL,
    last_name     varchar(100) NOT NULL,
    company       varchar(150),
    street        varchar(150) NOT NULL,
    house_number  varchar(20)  NOT NULL,
    address_line2 varchar(150),
    postal_code   varchar(20)  NOT NULL,
    city          varchar(100) NOT NULL,
    country_code  char(2)      NOT NULL,
    phone         varchar(40),
    PRIMARY KEY (order_id, type)
);

CREATE TABLE order_status_history (
    id                uuid PRIMARY KEY,
    order_id          uuid        NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    from_status       varchar(20),
    to_status         varchar(20) NOT NULL,
    note              varchar(1000),          -- internal note
    customer_message  varchar(1000),          -- shown to the customer, if any
    changed_by        uuid        REFERENCES users(id) ON DELETE SET NULL,   -- null = system
    created_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_order_status_history_order      ON order_status_history (order_id, created_at);
CREATE INDEX idx_order_status_history_changed_by ON order_status_history (changed_by);

-- The stock ledger can now point at the order that caused a movement.
ALTER TABLE stock_movements
    ADD CONSTRAINT fk_stock_movements_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL;
CREATE INDEX idx_stock_movements_order ON stock_movements (order_id);
