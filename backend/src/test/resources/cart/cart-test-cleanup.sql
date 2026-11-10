DELETE FROM products WHERE slug IN ('ct-alpha', 'ct-beta', 'ct-sold', 'ct-draft');
DELETE FROM categories WHERE slug = 'ct-category';
DELETE FROM species WHERE slug = 'ct-species';
