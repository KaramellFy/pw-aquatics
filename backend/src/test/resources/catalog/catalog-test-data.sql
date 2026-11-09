-- Small, purpose-built catalog for CatalogApiTest.
INSERT INTO categories (id, parent_id, name, slug, sort_order) VALUES
    ('00000000-0000-0000-0000-0000000000c1', NULL, 'Shrimp', 't-shrimp', 1),
    ('00000000-0000-0000-0000-0000000000c2', '00000000-0000-0000-0000-0000000000c1', 'Neocaridina', 't-neo', 1),
    ('00000000-0000-0000-0000-0000000000c3', NULL, 'Snails', 't-snails', 2);

INSERT INTO species (id, scientific_name, common_name, slug, temp_min_c, temp_max_c, difficulty, trade_restricted, restriction_note) VALUES
    ('00000000-0000-0000-0000-0000000000a1', 'Testus visibilis', 'Visible shrimp', 't-visible', 18, 26, 'BEGINNER', false, NULL),
    ('00000000-0000-0000-0000-0000000000a2', 'Testus bannedus', 'Banned crayfish', 't-banned', NULL, NULL, NULL, true, 'Invasive');

INSERT INTO colours (id, name, slug, hex_code, sort_order) VALUES
    ('00000000-0000-0000-0000-0000000000d1', 'Red', 't-red', '#C62828', 1),
    ('00000000-0000-0000-0000-0000000000d2', 'Blue', 't-blue', '#1565C0', 2);

INSERT INTO grades (id, name, slug, scale, rank, sort_order) VALUES
    ('00000000-0000-0000-0000-0000000000e1', 'Low', 't-low', 'TEST', 1, 1),
    ('00000000-0000-0000-0000-0000000000e2', 'High', 't-high', 'TEST', 2, 2);

INSERT INTO products (id, slug, name, species_id, category_id, grade_id, short_description, description, status, featured,
                      stock_units, low_stock_threshold, available_from, min_price_cents, published_at) VALUES
    ('00000000-0000-0000-0000-000000000101', 'alpha-blue', 'Alpha Blue', '00000000-0000-0000-0000-0000000000a1',
     '00000000-0000-0000-0000-0000000000c2', '00000000-0000-0000-0000-0000000000e2', 'Blue shrimp',
     'Deep **blue**. <script>alert(1)</script> [x](javascript:alert(1))', 'ACTIVE', true, 20, 5, NULL, 300, '2026-01-02T09:00:00Z'),
    ('00000000-0000-0000-0000-000000000102', 'beta-red', 'Beta Red', '00000000-0000-0000-0000-0000000000a1',
     '00000000-0000-0000-0000-0000000000c2', '00000000-0000-0000-0000-0000000000e1', 'Red shrimp', NULL,
     'ACTIVE', false, 3, 5, NULL, 200, '2026-01-03T09:00:00Z'),
    ('00000000-0000-0000-0000-000000000103', 'gamma-snail', 'Gamma Snail', NULL,
     '00000000-0000-0000-0000-0000000000c3', NULL, 'Snail', NULL,
     'ACTIVE', false, 0, 5, '2027-01-01', 500, '2026-01-04T09:00:00Z'),
    ('00000000-0000-0000-0000-000000000104', 'delta-draft', 'Delta Draft', '00000000-0000-0000-0000-0000000000a1',
     '00000000-0000-0000-0000-0000000000c2', NULL, 'Draft', NULL,
     'DRAFT', false, 10, 5, NULL, 100, NULL),
    ('00000000-0000-0000-0000-000000000105', 'epsilon-banned', 'Epsilon Banned', '00000000-0000-0000-0000-0000000000a2',
     '00000000-0000-0000-0000-0000000000c1', NULL, 'Banned', NULL,
     'ACTIVE', false, 10, 5, NULL, 100, '2026-01-05T09:00:00Z'),
    ('00000000-0000-0000-0000-000000000106', 'zeta-sold', 'Zeta Sold', '00000000-0000-0000-0000-0000000000a1',
     '00000000-0000-0000-0000-0000000000c2', NULL, 'Sold out', NULL,
     'ACTIVE', false, 0, 5, NULL, 900, '2026-01-06T09:00:00Z');

INSERT INTO product_colours (product_id, colour_id) VALUES
    ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-0000000000d2'),
    ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-0000000000d1'),
    ('00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-0000000000d1'),
    ('00000000-0000-0000-0000-000000000106', '00000000-0000-0000-0000-0000000000d1');

INSERT INTO product_variants (id, product_id, sku, name, pack_size, price_cents, compare_at_price_cents, sort_order) VALUES
    ('00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000101', 'T-ALPHA-1', '1 animal', 1, 300, NULL, 0),
    ('00000000-0000-0000-0000-000000000202', '00000000-0000-0000-0000-000000000101', 'T-ALPHA-10', '10 animals', 10, 2500, 3000, 1),
    ('00000000-0000-0000-0000-000000000203', '00000000-0000-0000-0000-000000000102', 'T-BETA-1', '1 animal', 1, 200, NULL, 0),
    ('00000000-0000-0000-0000-000000000204', '00000000-0000-0000-0000-000000000102', 'T-BETA-10', '10 animals', 10, 1800, NULL, 1),
    ('00000000-0000-0000-0000-000000000205', '00000000-0000-0000-0000-000000000103', 'T-GAMMA-1', '1 snail', 1, 500, NULL, 0),
    ('00000000-0000-0000-0000-000000000206', '00000000-0000-0000-0000-000000000104', 'T-DELTA-1', '1 animal', 1, 100, NULL, 0),
    ('00000000-0000-0000-0000-000000000207', '00000000-0000-0000-0000-000000000105', 'T-EPS-1', '1 animal', 1, 100, NULL, 0),
    ('00000000-0000-0000-0000-000000000208', '00000000-0000-0000-0000-000000000106', 'T-ZETA-1', '1 animal', 1, 900, NULL, 0);
