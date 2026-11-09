DELETE FROM products WHERE slug IN ('alpha-blue', 'beta-red', 'gamma-snail', 'delta-draft', 'epsilon-banned', 'zeta-sold');
DELETE FROM categories WHERE slug = 't-neo';
DELETE FROM categories WHERE slug IN ('t-shrimp', 't-snails');
DELETE FROM species WHERE slug IN ('t-visible', 't-banned');
DELETE FROM colours WHERE slug IN ('t-red', 't-blue');
DELETE FROM grades WHERE slug IN ('t-low', 't-high');
