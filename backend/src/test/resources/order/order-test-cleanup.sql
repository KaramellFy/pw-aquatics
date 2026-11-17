DELETE FROM shop_settings WHERE key = 'shipping.pause';
DELETE FROM payment_refunds WHERE payment_id IN (SELECT p.id FROM payments p JOIN orders o ON o.id = p.order_id
    WHERE o.shipping_method_code IN ('TEST_LIVE', 'TEST_PARCEL'));
DELETE FROM payments WHERE order_id IN (SELECT id FROM orders WHERE shipping_method_code IN ('TEST_LIVE', 'TEST_PARCEL'));
DELETE FROM payment_webhook_events WHERE event_id LIKE 'evt_test_%';
DELETE FROM orders WHERE shipping_method_code IN ('TEST_LIVE', 'TEST_PARCEL');
DELETE FROM shipping_methods WHERE code IN ('TEST_LIVE', 'TEST_PARCEL');
DELETE FROM products WHERE slug IN ('ot-alpha', 'ot-last', 'ot-food', 'ot-packs');
DELETE FROM categories WHERE slug = 'ot-category';
DELETE FROM species WHERE slug = 'ot-species';
