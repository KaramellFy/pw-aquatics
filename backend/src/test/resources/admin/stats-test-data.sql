-- Orders, stock movements and accounts for AdminStatisticsApiTest (ids start with 3333…).
-- Everything lies in January 2001 so no other test's data falls into the periods queried.
-- Period under test: Mon 2001-01-08 – Sun 2001-01-14; previous period: 2001-01-01 – 2001-01-07.
-- January in Berlin is UTC+1.
INSERT INTO categories (id, name, slug, sort_order) VALUES
    ('33333333-0000-0000-0000-0000000000c1', 'Stats cat A', 'st-cat-a', 97),
    ('33333333-0000-0000-0000-0000000000c2', 'Stats cat B', 'st-cat-b', 97);
INSERT INTO species (id, scientific_name, slug) VALUES
    ('33333333-0000-0000-0000-0000000000a1', 'Statsia rubra', 'st-rubra'),
    ('33333333-0000-0000-0000-0000000000a2', 'Statsia caerulea', 'st-caerulea');
-- Archived, so the public catalog tests never see them.
INSERT INTO products (id, slug, name, species_id, category_id, status, stock_units, low_stock_threshold, is_live_animal) VALUES
    ('33333333-0000-0000-0000-000000000101', 'st-red', 'Stats Red', '33333333-0000-0000-0000-0000000000a1',
     '33333333-0000-0000-0000-0000000000c1', 'ARCHIVED', 100, 5, true),
    ('33333333-0000-0000-0000-000000000102', 'st-blue', 'Stats Blue', '33333333-0000-0000-0000-0000000000a2',
     '33333333-0000-0000-0000-0000000000c2', 'ARCHIVED', 100, 5, true);

INSERT INTO orders (id, order_number, customer_email, status, payment_status, payment_method, subtotal_cents,
                    shipping_cents, total_cents, tax_cents, shipping_method_code, shipping_method_name,
                    terms_accepted_at, placed_at) VALUES
    -- previous period: one delivered order
    ('33333333-0000-0000-0000-000000000400', 'ST-2001-000000', 'z@stats.test', 'DELIVERED', 'PAID', 'BANK_TRANSFER',
     1500, 500, 2000, 319, 'ST', 'Stats', '2001-01-03T09:00:00Z', '2001-01-03T09:00:00Z'),
    -- Monday 8th: paid (DE) and awaiting payment (GB)
    ('33333333-0000-0000-0000-000000000401', 'ST-2001-000001', 'a@stats.test', 'PAID', 'PAID', 'BANK_TRANSFER',
     3000, 990, 3990, 637, 'ST', 'Stats', '2001-01-08T10:00:00Z', '2001-01-08T10:00:00Z'),
    ('33333333-0000-0000-0000-000000000402', 'ST-2001-000002', 'b@stats.test', 'PENDING_PAYMENT', 'UNPAID', 'BANK_TRANSFER',
     2500, 990, 3490, 557, 'ST', 'Stats', '2001-01-08T12:00:00Z', '2001-01-08T12:00:00Z'),
    -- 23:30 UTC on Wednesday 10th is 00:30 on Thursday 11th in Berlin; same customer as order 1
    ('33333333-0000-0000-0000-000000000403', 'ST-2001-000003', 'a@stats.test', 'PROCESSING', 'PAID', 'BANK_TRANSFER',
     7000, 0, 7000, 958, 'ST', 'Stats', '2001-01-10T23:30:00Z', '2001-01-10T23:30:00Z'),
    -- Friday 12th: cancelled, not a sale
    ('33333333-0000-0000-0000-000000000404', 'ST-2001-000004', 'c@stats.test', 'CANCELLED', 'UNPAID', 'BANK_TRANSFER',
     300, 990, 1290, 206, 'ST', 'Stats', '2001-01-12T09:00:00Z', '2001-01-12T09:00:00Z');

INSERT INTO order_items (id, order_id, product_id, position, sku, product_name, variant_name, species_name, grade_name,
                         is_live_animal, pack_size, unit_price_cents, quantity, line_total_cents, tax_rate_bps, tax_cents) VALUES
    ('33333333-0000-0000-0000-000000000500', '33333333-0000-0000-0000-000000000400', '33333333-0000-0000-0000-000000000101',
     0, 'ST-RED-1', 'Stats Red', '1 animal', 'Statsia rubra', NULL, true, 1, 300, 5, 1500, 1900, 239),
    ('33333333-0000-0000-0000-000000000501', '33333333-0000-0000-0000-000000000401', '33333333-0000-0000-0000-000000000101',
     0, 'ST-RED-1', 'Stats Red (old name)', '1 animal', 'Statsia rubra', NULL, true, 1, 300, 10, 3000, 1900, 479),
    ('33333333-0000-0000-0000-000000000502', '33333333-0000-0000-0000-000000000402', '33333333-0000-0000-0000-000000000102',
     0, 'ST-BLUE-10', 'Stats Blue', '10 animals', 'Statsia caerulea', 'High', true, 10, 2500, 1, 2500, 1900, 399),
    ('33333333-0000-0000-0000-000000000503', '33333333-0000-0000-0000-000000000403', '33333333-0000-0000-0000-000000000101',
     0, 'ST-RED-1', 'Stats Red', '1 animal', 'Statsia rubra', NULL, true, 1, 300, 20, 6000, 1900, 958),
    -- a product that no longer exists and is not a live animal
    ('33333333-0000-0000-0000-000000000504', '33333333-0000-0000-0000-000000000403', NULL,
     1, 'ST-FOOD', 'Old food', '50 g', NULL, NULL, false, 1, 500, 2, 1000, 0, 0),
    ('33333333-0000-0000-0000-000000000505', '33333333-0000-0000-0000-000000000404', '33333333-0000-0000-0000-000000000101',
     0, 'ST-RED-1', 'Stats Red', '1 animal', 'Statsia rubra', NULL, true, 1, 300, 1, 300, 1900, 48);

INSERT INTO order_addresses (order_id, type, first_name, last_name, street, house_number, postal_code, city, country_code) VALUES
    ('33333333-0000-0000-0000-000000000400', 'SHIPPING', 'Zoe', 'Stats', 'Weg', '1', '10115', 'Berlin', 'DE'),
    ('33333333-0000-0000-0000-000000000401', 'SHIPPING', 'Anna', 'Stats', 'Weg', '1', '10115', 'Berlin', 'DE'),
    ('33333333-0000-0000-0000-000000000402', 'SHIPPING', 'Ben', 'Stats', 'Road', '2', 'SW1A 1AA', 'London', 'GB'),
    ('33333333-0000-0000-0000-000000000403', 'SHIPPING', 'Anna', 'Stats', 'Weg', '1', '10115', 'Berlin', 'DE'),
    ('33333333-0000-0000-0000-000000000404', 'SHIPPING', 'Carl', 'Stats', 'Gasse', '3', '1010', 'Wien', 'AT');

INSERT INTO stock_movements (id, product_id, delta, stock_after, reason, created_at) VALUES
    -- previous period: restock 20, sold 5, lost 1
    ('33333333-0000-0000-0000-000000000600', '33333333-0000-0000-0000-000000000101', 20, 120, 'RESTOCK', '2001-01-02T08:00:00Z'),
    ('33333333-0000-0000-0000-000000000601', '33333333-0000-0000-0000-000000000101', -5, 115, 'ORDER_PLACED', '2001-01-03T09:00:00Z'),
    ('33333333-0000-0000-0000-000000000602', '33333333-0000-0000-0000-000000000101', -1, 114, 'LOSS', '2001-01-05T09:00:00Z'),
    -- period: restock 50, sold 41 - 1 returned by the cancellation = 40, lost 5
    ('33333333-0000-0000-0000-000000000610', '33333333-0000-0000-0000-000000000101', 50, 164, 'RESTOCK', '2001-01-08T07:00:00Z'),
    ('33333333-0000-0000-0000-000000000611', '33333333-0000-0000-0000-000000000101', -10, 154, 'ORDER_PLACED', '2001-01-08T10:00:00Z'),
    ('33333333-0000-0000-0000-000000000612', '33333333-0000-0000-0000-000000000102', -10, 90, 'ORDER_PLACED', '2001-01-08T12:00:00Z'),
    ('33333333-0000-0000-0000-000000000613', '33333333-0000-0000-0000-000000000101', -20, 134, 'ORDER_PLACED', '2001-01-10T23:30:00Z'),
    ('33333333-0000-0000-0000-000000000614', '33333333-0000-0000-0000-000000000101', -1, 133, 'ORDER_PLACED', '2001-01-12T09:00:00Z'),
    ('33333333-0000-0000-0000-000000000615', '33333333-0000-0000-0000-000000000101', 1, 134, 'ORDER_CANCELLED', '2001-01-12T10:00:00Z'),
    ('33333333-0000-0000-0000-000000000616', '33333333-0000-0000-0000-000000000101', -3, 131, 'LOSS', '2001-01-13T09:00:00Z'),
    ('33333333-0000-0000-0000-000000000617', '33333333-0000-0000-0000-000000000102', -2, 88, 'LOSS', '2001-01-14T09:00:00Z');

-- Registrations: two customers (and one admin, not counted) in the period, one customer before.
INSERT INTO users (id, email, password_hash, first_name, last_name, role, created_at) VALUES
    ('33333333-0000-0000-0000-000000000700', 'u0@stats.test', 'x', 'U', 'Zero', 'CUSTOMER', '2001-01-04T12:00:00Z'),
    ('33333333-0000-0000-0000-000000000701', 'u1@stats.test', 'x', 'U', 'One', 'CUSTOMER', '2001-01-09T12:00:00Z'),
    ('33333333-0000-0000-0000-000000000702', 'u2@stats.test', 'x', 'U', 'Two', 'CUSTOMER', '2001-01-14T22:59:00Z'),
    ('33333333-0000-0000-0000-000000000703', 'u3@stats.test', 'x', 'U', 'Admin', 'ADMIN', '2001-01-10T12:00:00Z'),
    -- 23:30 UTC on Sunday 14th is already Monday 15th in Berlin: outside the period
    ('33333333-0000-0000-0000-000000000704', 'u4@stats.test', 'x', 'U', 'Late', 'CUSTOMER', '2001-01-14T23:30:00Z');
