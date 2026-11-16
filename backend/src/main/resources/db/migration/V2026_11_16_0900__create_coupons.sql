-- Phase 8: coupons (docs/DATABASE.md §3.5).

CREATE TABLE coupons (
    id                        uuid PRIMARY KEY,
    -- Stored in upper case; citext keeps the lookup case-insensitive. Never changes.
    code                      citext       NOT NULL UNIQUE CHECK (length(code) BETWEEN 3 AND 32),
    description               varchar(255),
    type                      varchar(20)  NOT NULL CHECK (type IN ('PERCENTAGE', 'FIXED_AMOUNT', 'FREE_SHIPPING')),
    -- PERCENTAGE: basis points (1000 = 10 %); FIXED_AMOUNT: cents; FREE_SHIPPING: 0
    value                     bigint       NOT NULL,
    -- Goods value (before the discount, without shipping) an order must reach.
    min_order_cents           bigint       NOT NULL DEFAULT 0 CHECK (min_order_cents >= 0),
    max_discount_cents        bigint       CHECK (max_discount_cents > 0),   -- cap for percentage coupons
    starts_at                 timestamptz,
    ends_at                   timestamptz,                                    -- exclusive
    usage_limit_total         int          CHECK (usage_limit_total > 0),
    usage_limit_per_customer  int          CHECK (usage_limit_per_customer > 0),
    -- Redemptions of orders that were not cancelled; changed only by a conditional update.
    times_redeemed            int          NOT NULL DEFAULT 0 CHECK (times_redeemed >= 0),
    active                    boolean      NOT NULL DEFAULT true,
    created_by                uuid         REFERENCES users(id) ON DELETE SET NULL,
    created_at                timestamptz  NOT NULL DEFAULT now(),
    updated_at                timestamptz  NOT NULL DEFAULT now(),
    version                   bigint       NOT NULL DEFAULT 0,
    CHECK (CASE type
               WHEN 'PERCENTAGE'    THEN value BETWEEN 1 AND 10000
               WHEN 'FIXED_AMOUNT'  THEN value >= 1 AND max_discount_cents IS NULL
               ELSE                      value = 0 AND max_discount_cents IS NULL
           END),
    CHECK (starts_at IS NULL OR ends_at IS NULL OR starts_at < ends_at)
);
CREATE INDEX idx_coupons_created_by ON coupons (created_by);

-- One row per order that used a coupon. Kept when the order is cancelled (released_at is set
-- and the coupon's counter goes down again), so the history stays complete.
CREATE TABLE coupon_redemptions (
    id             uuid PRIMARY KEY,
    coupon_id      uuid        NOT NULL REFERENCES coupons(id) ON DELETE RESTRICT,
    order_id       uuid        NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    user_id        uuid        REFERENCES users(id) ON DELETE SET NULL,
    -- What the customer saved: the discount on goods, or the waived shipping price.
    discount_cents bigint      NOT NULL CHECK (discount_cents >= 0),
    redeemed_at    timestamptz NOT NULL,
    released_at    timestamptz
);
CREATE INDEX idx_coupon_redemptions_coupon_user ON coupon_redemptions (coupon_id, user_id);
CREATE INDEX idx_coupon_redemptions_user        ON coupon_redemptions (user_id);

ALTER TABLE carts ADD COLUMN coupon_id uuid REFERENCES coupons(id) ON DELETE SET NULL;
CREATE INDEX idx_carts_coupon ON carts (coupon_id);
