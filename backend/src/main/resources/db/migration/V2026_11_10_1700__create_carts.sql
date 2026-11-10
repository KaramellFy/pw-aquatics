-- Shopping carts (docs/DATABASE.md §3.6). The cart stores no prices: prices, totals and
-- availability are recalculated from the catalog on every read.
-- carts.coupon_id is added with coupons (Phase 8).

CREATE TABLE carts (
    id                    uuid PRIMARY KEY,
    -- A customer has at most one cart.
    user_id               uuid        UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    -- SHA-256 of the guest cart cookie; the raw token exists only in the browser.
    anonymous_token_hash  char(64)    UNIQUE,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    -- Guest carts only; removed by a cleanup job after this time.
    expires_at            timestamptz,
    CHECK ((user_id IS NULL) <> (anonymous_token_hash IS NULL))
);
CREATE INDEX idx_carts_expires ON carts (expires_at) WHERE expires_at IS NOT NULL;

CREATE TABLE cart_items (
    id          uuid PRIMARY KEY,
    cart_id     uuid        NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    variant_id  uuid        NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    quantity    int         NOT NULL CHECK (quantity BETWEEN 1 AND 999),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (cart_id, variant_id)
);
CREATE INDEX idx_cart_items_variant ON cart_items (variant_id);
