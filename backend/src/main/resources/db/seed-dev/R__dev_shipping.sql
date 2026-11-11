-- Dev-only shipping methods. The structure follows the owner's setup (DHL, dispatch Monday
-- to Wednesday, delivery to the EU and the United Kingdom).
-- THE PRICES AND TRANSIT TIMES ARE PLACEHOLDERS, not the shop's real rates.
INSERT INTO shipping_methods
    (id, code, name, description, carrier, price_cents, free_shipping_threshold_cents,
     live_animals_allowed, dispatch_weekdays, estimated_days_min, estimated_days_max, sort_order)
VALUES
    ('5a1f0000-0000-7000-8000-000000000001', 'DHL_LIVE_DE', 'DHL Express – Germany',
     'Insulated box, dispatched Monday to Wednesday so no animal spends a weekend in transit.',
     'DHL', 990, NULL, true, '{1,2,3}', 1, 1, 0),
    ('5a1f0000-0000-7000-8000-000000000002', 'DHL_LIVE_EU', 'DHL Express – European Union',
     'Insulated box, dispatched Monday to Wednesday so no animal spends a weekend in transit.',
     'DHL', 2490, NULL, true, '{1,2,3}', 1, 3, 1),
    ('5a1f0000-0000-7000-8000-000000000003', 'DHL_LIVE_UK', 'DHL Express – United Kingdom',
     'Insulated box, dispatched Monday to Wednesday. Customs clearance can add a day.',
     'DHL', 3990, NULL, true, '{1,2,3}', 2, 3, 2)
ON CONFLICT (id) DO UPDATE SET
    code = EXCLUDED.code, name = EXCLUDED.name, description = EXCLUDED.description, carrier = EXCLUDED.carrier,
    price_cents = EXCLUDED.price_cents, free_shipping_threshold_cents = EXCLUDED.free_shipping_threshold_cents,
    live_animals_allowed = EXCLUDED.live_animals_allowed, dispatch_weekdays = EXCLUDED.dispatch_weekdays,
    estimated_days_min = EXCLUDED.estimated_days_min, estimated_days_max = EXCLUDED.estimated_days_max,
    sort_order = EXCLUDED.sort_order, updated_at = now();

INSERT INTO shipping_method_countries (shipping_method_id, country_code) VALUES
    ('5a1f0000-0000-7000-8000-000000000001', 'DE'),
    ('5a1f0000-0000-7000-8000-000000000003', 'GB')
ON CONFLICT DO NOTHING;

-- The other 26 EU member states.
INSERT INTO shipping_method_countries (shipping_method_id, country_code)
SELECT '5a1f0000-0000-7000-8000-000000000002', code
FROM unnest(ARRAY['AT', 'BE', 'BG', 'HR', 'CY', 'CZ', 'DK', 'EE', 'FI', 'FR', 'GR', 'HU', 'IE', 'IT',
                  'LV', 'LT', 'LU', 'MT', 'NL', 'PL', 'PT', 'RO', 'SK', 'SI', 'ES', 'SE']) AS code
ON CONFLICT DO NOTHING;
