DELETE FROM orders WHERE shipping_method_code = 'TEST_COUPON_LIVE';
DELETE FROM coupons WHERE code LIKE 'T8-%';
DELETE FROM shipping_methods WHERE code = 'TEST_COUPON_LIVE';
DELETE FROM products WHERE slug IN ('ct-alpha', 'ct-sale');
DELETE FROM categories WHERE slug = 'ct-category';
