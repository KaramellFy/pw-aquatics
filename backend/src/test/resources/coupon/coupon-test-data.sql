-- Catalog, shipping method and coupons for CouponApiTest (ids start with 8888…, codes with T8-).
INSERT INTO categories (id, name, slug, sort_order) VALUES
    ('88888888-0000-0000-0000-0000000000c1', 'Coupon test', 'ct-category', 97);
INSERT INTO products (id, slug, name, category_id, status, stock_units, low_stock_threshold, min_price_cents, published_at, is_live_animal) VALUES
    ('88888888-0000-0000-0000-000000000101', 'ct-alpha', 'Coupon Alpha', '88888888-0000-0000-0000-0000000000c1',
     'ACTIVE', 500, 5, 1000, '2026-01-02T09:00:00Z', true),
    ('88888888-0000-0000-0000-000000000102', 'ct-sale', 'Coupon Sale', '88888888-0000-0000-0000-0000000000c1',
     'ACTIVE', 500, 5, 1000, '2026-01-03T09:00:00Z', true);
INSERT INTO product_variants (id, product_id, sku, name, pack_size, price_cents, compare_at_price_cents, sort_order) VALUES
    ('88888888-0000-0000-0000-000000000201', '88888888-0000-0000-0000-000000000101', 'CT-ALPHA-1', '1 animal', 1, 1000, NULL, 0),
    ('88888888-0000-0000-0000-000000000202', '88888888-0000-0000-0000-000000000102', 'CT-SALE-1', '1 animal', 1, 1000, 1500, 0);

INSERT INTO shipping_methods (id, code, name, carrier, price_cents, free_shipping_threshold_cents, live_animals_allowed,
                              dispatch_weekdays, estimated_days_min, estimated_days_max, sort_order) VALUES
    ('88888888-0000-0000-0000-000000000301', 'TEST_COUPON_LIVE', 'Coupon test express', 'DHL', 990, 10000, true,
     '{1,2,3}', 1, 2, 0);
INSERT INTO shipping_method_countries (shipping_method_id, country_code) VALUES
    ('88888888-0000-0000-0000-000000000301', 'DE');

INSERT INTO coupons (id, code, description, type, value, min_order_cents, max_discount_cents, starts_at, ends_at,
                     usage_limit_total, usage_limit_per_customer, active) VALUES
    ('88888888-0000-0000-0000-000000000401', 'T8-TEN', 'Ten percent', 'PERCENTAGE', 1000, 0, NULL, NULL, NULL, NULL, NULL, true),
    ('88888888-0000-0000-0000-000000000402', 'T8-FIVE', 'Five off from 30', 'FIXED_AMOUNT', 500, 3000, NULL, NULL, NULL, NULL, NULL, true),
    ('88888888-0000-0000-0000-000000000403', 'T8-SHIP', 'Free shipping', 'FREE_SHIPPING', 0, 0, NULL, NULL, NULL, NULL, NULL, true),
    ('88888888-0000-0000-0000-000000000404', 'T8-ONCE', 'Once per customer', 'PERCENTAGE', 1000, 0, NULL, NULL, NULL, NULL, 1, true),
    ('88888888-0000-0000-0000-000000000405', 'T8-LAST', 'Only one left', 'PERCENTAGE', 1000, 0, NULL, NULL, NULL, 1, NULL, true),
    ('88888888-0000-0000-0000-000000000406', 'T8-OLD', 'Expired', 'PERCENTAGE', 1000, 0, NULL, NULL, '2026-01-01T00:00:00Z', NULL, NULL, true),
    ('88888888-0000-0000-0000-000000000407', 'T8-LATER', 'Not yet', 'PERCENTAGE', 1000, 0, NULL, '2099-01-01T00:00:00Z', NULL, NULL, NULL, true),
    ('88888888-0000-0000-0000-000000000408', 'T8-OFF', 'Switched off', 'PERCENTAGE', 1000, 0, NULL, NULL, NULL, NULL, NULL, false);
