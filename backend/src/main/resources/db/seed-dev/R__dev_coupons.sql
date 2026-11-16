-- Dev-only demo coupons (loaded with the dev profile). Re-running updates the terms but never
-- the redemption counter.
INSERT INTO coupons (id, code, description, type, value, min_order_cents, max_discount_cents, starts_at, ends_at,
                     usage_limit_total, usage_limit_per_customer, active) VALUES
    ('7f0e8c1a-0000-4000-8000-00000000c001', 'WELCOME10', 'Demo: 10 % off, up to €20', 'PERCENTAGE', 1000, 0, 2000,
     NULL, NULL, NULL, 1, true),
    ('7f0e8c1a-0000-4000-8000-00000000c002', 'SHIPFREE', 'Demo: free shipping from €40', 'FREE_SHIPPING', 0, 4000, NULL,
     NULL, NULL, NULL, NULL, true),
    ('7f0e8c1a-0000-4000-8000-00000000c003', 'FIVEOFF', 'Demo: €5 off from €30', 'FIXED_AMOUNT', 500, 3000, NULL,
     NULL, NULL, 100, NULL, true)
ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code, description = EXCLUDED.description, type = EXCLUDED.type,
    value = EXCLUDED.value, min_order_cents = EXCLUDED.min_order_cents, max_discount_cents = EXCLUDED.max_discount_cents,
    starts_at = EXCLUDED.starts_at, ends_at = EXCLUDED.ends_at, usage_limit_total = EXCLUDED.usage_limit_total,
    usage_limit_per_customer = EXCLUDED.usage_limit_per_customer, active = EXCLUDED.active;
