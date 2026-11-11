DELETE FROM shop_settings WHERE key = 'shipping.pause';
DELETE FROM orders WHERE shipping_method_code IN ('TEST_LIVE', 'TEST_PARCEL');
DELETE FROM shipping_methods WHERE code IN ('TEST_LIVE', 'TEST_PARCEL');
DELETE FROM products WHERE slug IN ('ot-alpha', 'ot-last', 'ot-food', 'ot-packs');
DELETE FROM categories WHERE slug = 'ot-category';
DELETE FROM species WHERE slug = 'ot-species';
