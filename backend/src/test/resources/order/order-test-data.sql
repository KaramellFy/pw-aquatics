-- Catalog and shipping methods for CheckoutApiTest (ids start with 2222…).
INSERT INTO categories (id, name, slug, sort_order) VALUES
    ('22222222-0000-0000-0000-0000000000c1', 'Order test', 'ot-category', 98);
INSERT INTO species (id, scientific_name, slug) VALUES
    ('22222222-0000-0000-0000-0000000000a1', 'Ordera testa', 'ot-species');
INSERT INTO products (id, slug, name, species_id, category_id, status, stock_units, low_stock_threshold, min_price_cents, published_at, is_live_animal) VALUES
    ('22222222-0000-0000-0000-000000000101', 'ot-alpha', 'Order Alpha', '22222222-0000-0000-0000-0000000000a1',
     '22222222-0000-0000-0000-0000000000c1', 'ACTIVE', 100, 5, 300, '2026-01-02T09:00:00Z', true),
    ('22222222-0000-0000-0000-000000000102', 'ot-last', 'Order Last One', '22222222-0000-0000-0000-0000000000a1',
     '22222222-0000-0000-0000-0000000000c1', 'ACTIVE', 1, 5, 1500, '2026-01-03T09:00:00Z', true),
    ('22222222-0000-0000-0000-000000000103', 'ot-food', 'Order Food', NULL,
     '22222222-0000-0000-0000-0000000000c1', 'ACTIVE', 50, 5, 500, '2026-01-04T09:00:00Z', false),
    ('22222222-0000-0000-0000-000000000104', 'ot-packs', 'Order Packs', '22222222-0000-0000-0000-0000000000a1',
     '22222222-0000-0000-0000-0000000000c1', 'ACTIVE', 12, 5, 300, '2026-01-05T09:00:00Z', true);
INSERT INTO product_variants (id, product_id, sku, name, pack_size, price_cents, sort_order) VALUES
    ('22222222-0000-0000-0000-000000000201', '22222222-0000-0000-0000-000000000101', 'OT-ALPHA-1', '1 animal', 1, 300, 0),
    ('22222222-0000-0000-0000-000000000202', '22222222-0000-0000-0000-000000000101', 'OT-ALPHA-10', '10 animals', 10, 2500, 1),
    ('22222222-0000-0000-0000-000000000203', '22222222-0000-0000-0000-000000000102', 'OT-LAST-1', '1 animal', 1, 1500, 0),
    ('22222222-0000-0000-0000-000000000204', '22222222-0000-0000-0000-000000000103', 'OT-FOOD-1', '50 g', 1, 500, 0),
    ('22222222-0000-0000-0000-000000000205', '22222222-0000-0000-0000-000000000104', 'OT-PACKS-1', '1 animal', 1, 300, 0),
    ('22222222-0000-0000-0000-000000000206', '22222222-0000-0000-0000-000000000104', 'OT-PACKS-10', '10 animals', 10, 2500, 1);

INSERT INTO shipping_methods (id, code, name, carrier, price_cents, free_shipping_threshold_cents, live_animals_allowed,
                              dispatch_weekdays, estimated_days_min, estimated_days_max, sort_order) VALUES
    ('22222222-0000-0000-0000-000000000301', 'TEST_LIVE', 'Test live express', 'DHL', 990, 10000, true, '{1,2,3}', 1, 2, 0),
    ('22222222-0000-0000-0000-000000000302', 'TEST_PARCEL', 'Test parcel', 'DHL', 490, NULL, false, '{1,2,3,4,5}', 2, 4, 1);
INSERT INTO shipping_method_countries (shipping_method_id, country_code) VALUES
    ('22222222-0000-0000-0000-000000000301', 'DE'),
    ('22222222-0000-0000-0000-000000000301', 'GB'),
    ('22222222-0000-0000-0000-000000000302', 'DE');
