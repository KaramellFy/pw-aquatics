DELETE FROM orders WHERE order_number LIKE 'ST-2001-%';
DELETE FROM products WHERE slug IN ('st-red', 'st-blue');
DELETE FROM categories WHERE slug IN ('st-cat-a', 'st-cat-b');
DELETE FROM species WHERE slug IN ('st-rubra', 'st-caerulea');
DELETE FROM users WHERE email LIKE '%@stats.test';
