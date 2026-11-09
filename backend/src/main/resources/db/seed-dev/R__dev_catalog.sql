-- Dev-only demo catalog (loaded with the dev profile; generated, edit with care).
-- Re-running updates existing rows (ON CONFLICT ... DO UPDATE). Photos are added in Phase 6.

INSERT INTO categories (id, parent_id, name, slug, description, sort_order, active) VALUES
    ('ed3298c2-8c61-5b0b-9d80-552ecb9b9df6', NULL, 'Shrimp', 'shrimp', 'Neocaridina and Caridina in clean colours and strong lines.', 1, true),
    ('1ea04a42-3a48-524f-99f1-74c6289c75f1', 'ed3298c2-8c61-5b0b-9d80-552ecb9b9df6', 'Neocaridina', 'neocaridina', 'Hardy, colourful dwarf shrimp for most tap waters.', 1, true),
    ('e159ea8b-d874-5443-87f4-989139c3001d', 'ed3298c2-8c61-5b0b-9d80-552ecb9b9df6', 'Caridina', 'caridina', 'Bee, tiger and Amano shrimp, some needing soft water.', 2, true),
    ('68a437de-a786-5ca4-83f6-21cc045fe9f1', NULL, 'Snails', 'snails', 'Quiet helpers that keep a tank in balance.', 2, true),
    ('f238f676-740b-51e2-a77e-2cc63ae3a9ed', NULL, 'Crayfish', 'crayfish', 'Dwarf crayfish with real character.', 3, true),
    ('2ef12647-5cfb-51d1-ac94-de58518230fa', NULL, 'Crabs', 'crabs', 'Fascinating residents for specialised setups.', 4, true)
ON CONFLICT (id) DO UPDATE SET parent_id = EXCLUDED.parent_id, name = EXCLUDED.name, slug = EXCLUDED.slug, description = EXCLUDED.description, sort_order = EXCLUDED.sort_order, active = EXCLUDED.active;

INSERT INTO species (id, scientific_name, common_name, slug, description, origin, temp_min_c, temp_max_c, ph_min, ph_max, gh_min, gh_max, kh_min, kh_max, tds_min, tds_max, adult_size_mm, lifespan_months, difficulty, trade_restricted, restriction_note) VALUES
    ('c55b959a-d51e-5166-8933-f49af7bc00eb', 'Neocaridina davidi', 'Neocaridina shrimp', 'neocaridina-davidi', 'A hardy, peaceful dwarf shrimp bred in many colour forms. Thrives in most tap waters and planted tanks.', 'Taiwan, southern China', 18, 28, 6.5, 8.0, 6, 15, 2, 8, 150, 350, 30, 18, 'BEGINNER', false, NULL),
    ('0c979714-dfef-5d89-890c-b73df0dff46c', 'Caridina cantonensis', 'Bee shrimp', 'caridina-cantonensis', 'Crystal Red and related colour forms. Needs soft, slightly acidic water and very stable conditions.', 'Southern China (wild form); colour forms bred in Japan and Taiwan', 20, 24, 5.8, 6.8, 4, 6, 0, 1, 100, 200, 25, 18, 'ADVANCED', false, NULL),
    ('2985a6cd-fbdd-5025-b1d7-050ba168e3c3', 'Caridina mariae', 'Tiger shrimp', 'caridina-mariae', 'Striped, active shrimp that adapts to a wide range of soft to medium-hard water.', 'Southern China', 20, 25, 6.5, 7.5, 5, 10, 0, 5, 150, 250, 30, 24, 'INTERMEDIATE', false, NULL),
    ('d1829f19-ad9e-5624-afa4-7713bfba1bd7', 'Caridina multidentata', 'Amano shrimp', 'caridina-multidentata', 'A tireless algae eater. Its larvae need brackish water, so it does not breed in a freshwater tank.', 'Japan, Taiwan', 18, 28, 6.5, 8.0, 6, 15, 2, 10, 150, 400, 50, 36, 'BEGINNER', false, NULL),
    ('3280802e-3f61-5f45-873b-494c73ac69ad', 'Neritina natalensis', 'Zebra nerite snail', 'neritina-natalensis', 'Strikingly striped algae grazer. Eggs do not hatch in fresh water, so it will not overpopulate a tank.', 'East Africa', 22, 28, 7.0, 8.5, 8, 18, 4, 12, NULL, NULL, 25, 36, 'BEGINNER', false, NULL),
    ('e4863203-a908-5879-8f72-996d58ecf524', 'Planorbella duryi', 'Ramshorn snail', 'planorbella-duryi', 'A colourful clean-up crew member that eats leftover food and soft algae.', 'Florida, USA', 18, 28, 7.0, 8.0, 7, 18, 4, 15, NULL, NULL, 20, 12, 'BEGINNER', false, NULL),
    ('0cf95a1b-2c62-58d8-a5e9-58529caebfce', 'Cambarellus patzcuarensis', 'Mexican dwarf crayfish', 'cambarellus-patzcuarensis', 'A small, bright orange crayfish that leaves plants alone. Provide caves and plenty of hiding places.', 'Lake Pátzcuaro, Mexico', 15, 24, 7.0, 8.0, 8, 20, 5, 15, NULL, NULL, 40, 18, 'INTERMEDIATE', false, NULL),
    ('af34cefd-5eb2-5fa8-a5be-65ecb24c14c8', 'Limnopilos naiyanetri', 'Thai micro crab', 'limnopilos-naiyanetri', 'A tiny, fully aquatic crab that lives among roots and floating plants and filters food from the water.', 'Thailand', 22, 28, 6.5, 7.5, 5, 15, 2, 8, NULL, NULL, 10, 12, 'INTERMEDIATE', false, NULL),
    ('bdc5edc6-9a8d-5af2-bed6-286d8c5d9ee2', 'Procambarus virginalis', 'Marbled crayfish', 'procambarus-virginalis', NULL, 'Unknown (aquarium origin)', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 120, 36, NULL, true, 'Listed as an invasive alien species of Union concern (EU Regulation 1143/2014). Keeping, breeding and trading are prohibited in the EU. Shown for demonstration only.')
ON CONFLICT (id) DO UPDATE SET scientific_name = EXCLUDED.scientific_name, common_name = EXCLUDED.common_name, slug = EXCLUDED.slug, description = EXCLUDED.description, origin = EXCLUDED.origin, temp_min_c = EXCLUDED.temp_min_c, temp_max_c = EXCLUDED.temp_max_c, ph_min = EXCLUDED.ph_min, ph_max = EXCLUDED.ph_max, gh_min = EXCLUDED.gh_min, gh_max = EXCLUDED.gh_max, kh_min = EXCLUDED.kh_min, kh_max = EXCLUDED.kh_max, tds_min = EXCLUDED.tds_min, tds_max = EXCLUDED.tds_max, adult_size_mm = EXCLUDED.adult_size_mm, lifespan_months = EXCLUDED.lifespan_months, difficulty = EXCLUDED.difficulty, trade_restricted = EXCLUDED.trade_restricted, restriction_note = EXCLUDED.restriction_note;

INSERT INTO colours (id, name, slug, hex_code, sort_order) VALUES
    ('5cafffbf-320a-5d2e-b5de-9adc397aa9e5', 'Red', 'red', '#C62828', 1),
    ('3ed9e794-67c2-5d49-9c05-1d23bd84b11d', 'Orange', 'orange', '#EF6C00', 2),
    ('fa682888-85ce-5383-922f-d1fc9c321e11', 'Yellow', 'yellow', '#F9A825', 3),
    ('6dc50c21-47d8-5a0e-a1cb-2600103dc04c', 'Green', 'green', '#2E7D32', 4),
    ('bfc220d1-4841-5f39-b15b-63ee579b2feb', 'Blue', 'blue', '#1565C0', 5),
    ('6654a3ed-ce55-5e65-832a-b948543eea9e', 'Black', 'black', '#212121', 6),
    ('83a8e974-8e17-541a-a536-9b94a2448461', 'White', 'white', '#F5F5F5', 7),
    ('beeb9a91-85ef-58d9-97e1-963e1ac7286a', 'Brown', 'brown', '#6D4C41', 8),
    ('d436940a-155e-5b88-91b6-9850e9c2b2fb', 'Clear', 'clear', '#D9EEF2', 9)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, slug = EXCLUDED.slug, hex_code = EXCLUDED.hex_code, sort_order = EXCLUDED.sort_order;

INSERT INTO grades (id, name, slug, scale, description, rank, sort_order) VALUES
    ('da3f4254-eb0b-5c35-85ca-cfd8828b1656', 'Standard', 'standard', 'NEOCARIDINA', 'Good, even colour with some transparent areas.', 1, 1),
    ('bbd45d5e-84f1-5a66-8720-e29ac58afedd', 'High', 'high', 'NEOCARIDINA', 'Strong colour covering most of the body.', 2, 2),
    ('f4f7b814-2065-54aa-ad1f-6ac7ef85a52d', 'Premium', 'premium', 'NEOCARIDINA', 'Solid, fully covered colour including legs; our best selection.', 3, 3),
    ('4a603b26-9915-5095-93c9-76f82cb8261c', 'S', 's', 'CARIDINA_CRS', 'Crystal Red grade S: clear banding, some red on the head.', 1, 4),
    ('a85506de-fc2d-509d-984a-3f030b0f033d', 'SS', 'ss', 'CARIDINA_CRS', 'Crystal Red grade SS: more white, thicker bands.', 2, 5),
    ('ad66974e-cee6-54d9-96b9-0e41653e0058', 'SSS', 'sss', 'CARIDINA_CRS', 'Crystal Red grade SSS: mostly solid white with small red areas.', 3, 6)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, slug = EXCLUDED.slug, scale = EXCLUDED.scale, description = EXCLUDED.description, rank = EXCLUDED.rank, sort_order = EXCLUDED.sort_order;

INSERT INTO products (id, slug, name, species_id, category_id, grade_id, short_description, description, care_notes, status, is_live_animal, featured, stock_units, low_stock_threshold, available_from, tax_rate_bps, min_price_cents, seo_title, seo_description, published_at) VALUES
    ('c6f523c7-00f4-581f-ad2b-e6040df2536f', 'blue-dream-high', 'Blue Dream', 'c55b959a-d51e-5166-8933-f49af7bc00eb', '1ea04a42-3a48-524f-99f1-74c6289c75f1', 'bbd45d5e-84f1-5a66-8720-e29ac58afedd', 'Deep blue dwarf shrimp with even colour – a calm accent for green planted tanks.', 'Our Blue Dream line shows a **deep, even blue** that stands out against plants and dark substrate.

**Water:** see the care parameters. Acclimatise slowly (drip method) over at least an hour.', NULL, 'ACTIVE', true, true, 48, 5, NULL, 1900, 390, NULL, 'Deep blue dwarf shrimp with even colour – a calm accent for green planted tanks.', '2026-10-01 09:00:00+00'),
    ('6d2fe5be-b74c-557b-8da2-5979709d1277', 'blue-dream-premium', 'Blue Dream', 'c55b959a-d51e-5166-8933-f49af7bc00eb', '1ea04a42-3a48-524f-99f1-74c6289c75f1', 'f4f7b814-2065-54aa-ad1f-6ac7ef85a52d', 'Our most intense Blue Dream selection, colour down to the legs.', 'Hand-picked from our best Blue Dream tanks: **solid, intense blue** including legs and tail.

**Water:** see the care parameters. Acclimatise slowly (drip method) over at least an hour.', NULL, 'ACTIVE', true, false, 6, 8, NULL, 1900, 590, NULL, 'Our most intense Blue Dream selection, colour down to the legs.', '2026-10-02 09:00:00+00'),
    ('0b1ce6d8-8996-5711-92b8-c19004d01224', 'red-fire-standard', 'Red Fire', 'c55b959a-d51e-5166-8933-f49af7bc00eb', '1ea04a42-3a48-524f-99f1-74c6289c75f1', 'da3f4254-eb0b-5c35-85ca-cfd8828b1656', 'The classic red dwarf shrimp – hardy, peaceful and great for beginners.', 'Red Fire shrimp are the easiest way to start with shrimp: **hardy, active and colourful**.

**Water:** see the care parameters. Acclimatise slowly (drip method) over at least an hour.', NULL, 'ACTIVE', true, false, 120, 10, NULL, 1900, 190, NULL, 'The classic red dwarf shrimp – hardy, peaceful and great for beginners.', '2026-10-03 09:00:00+00'),
    ('ae66fe41-0d19-5e1d-a8dd-d11bb4474669', 'bloody-mary-premium', 'Bloody Mary', 'c55b959a-d51e-5166-8933-f49af7bc00eb', '1ea04a42-3a48-524f-99f1-74c6289c75f1', 'f4f7b814-2065-54aa-ad1f-6ac7ef85a52d', 'Glossy, deep red shrimp with an almost transparent body shell.', 'Bloody Mary shrimp show a **dark, glassy red**. Best on dark substrate with plenty of plants.

**Water:** see the care parameters. Acclimatise slowly (drip method) over at least an hour.', NULL, 'ACTIVE', true, true, 30, 5, NULL, 1900, 450, NULL, 'Glossy, deep red shrimp with an almost transparent body shell.', '2026-10-04 09:00:00+00'),
    ('f06d6250-947d-54e3-818c-d5c4af711710', 'yellow-fire-high', 'Yellow Fire', 'c55b959a-d51e-5166-8933-f49af7bc00eb', '1ea04a42-3a48-524f-99f1-74c6289c75f1', 'bbd45d5e-84f1-5a66-8720-e29ac58afedd', 'Bright yellow dwarf shrimp. The next group is growing up in our tanks.', 'Yellow Fire shrimp bring a **warm, bright yellow** into the tank.

**Water:** see the care parameters. Acclimatise slowly (drip method) over at least an hour.', NULL, 'ACTIVE', true, false, 0, 5, '2026-12-15', 1900, 290, NULL, 'Bright yellow dwarf shrimp. The next group is growing up in our tanks.', '2026-10-05 09:00:00+00'),
    ('f2459026-646a-5319-9ac4-82c5f1f0bd45', 'orange-sakura-standard', 'Orange Sakura', 'c55b959a-d51e-5166-8933-f49af7bc00eb', '1ea04a42-3a48-524f-99f1-74c6289c75f1', 'da3f4254-eb0b-5c35-85ca-cfd8828b1656', 'Warm orange colour form of the Neocaridina dwarf shrimp.', 'Orange Sakura shrimp are **easy, peaceful and eye-catching** in groups.

**Water:** see the care parameters. Acclimatise slowly (drip method) over at least an hour.', NULL, 'ACTIVE', true, false, 40, 5, NULL, 1900, 220, NULL, 'Warm orange colour form of the Neocaridina dwarf shrimp.', '2026-10-06 09:00:00+00'),
    ('1f898b15-185c-58fd-951b-dd0e4e2837e1', 'green-jade-high', 'Green Jade', 'c55b959a-d51e-5166-8933-f49af7bc00eb', '1ea04a42-3a48-524f-99f1-74c6289c75f1', 'bbd45d5e-84f1-5a66-8720-e29ac58afedd', 'A rare, glossy green colour form – subtle among plants, striking up close.', 'Green Jade shrimp have a **glossy, jade-green** shell that changes with the light.

**Water:** see the care parameters. Acclimatise slowly (drip method) over at least an hour.', NULL, 'ACTIVE', true, false, 25, 5, NULL, 1900, 350, NULL, 'A rare, glossy green colour form – subtle among plants, striking up close.', '2026-10-07 09:00:00+00'),
    ('c610b173-3bdc-575e-953e-1e07d383b30a', 'black-rose-premium', 'Black Rose', 'c55b959a-d51e-5166-8933-f49af7bc00eb', '1ea04a42-3a48-524f-99f1-74c6289c75f1', 'f4f7b814-2065-54aa-ad1f-6ac7ef85a52d', 'Deep black dwarf shrimp with a velvety finish.', 'Black Rose shrimp are **velvety black** and look stunning against light sand.

**Water:** see the care parameters. Acclimatise slowly (drip method) over at least an hour.', NULL, 'ACTIVE', true, false, 0, 5, NULL, 1900, 490, NULL, 'Deep black dwarf shrimp with a velvety finish.', '2026-10-08 09:00:00+00'),
    ('4930bdf9-8aed-542c-8e83-6b8dbaa01dd7', 'crystal-red-ss', 'Crystal Red', '0c979714-dfef-5d89-890c-b73df0dff46c', 'e159ea8b-d874-5443-87f4-989139c3001d', 'a85506de-fc2d-509d-984a-3f030b0f033d', 'Classic red and white banded bee shrimp in grade SS.', 'Crystal Red shrimp need **soft, acidic water** (active soil recommended).

**Water:** see the care parameters. Acclimatise slowly (drip method) over at least an hour.', NULL, 'ACTIVE', true, true, 22, 5, NULL, 1900, 690, NULL, 'Classic red and white banded bee shrimp in grade SS.', '2026-10-09 09:00:00+00'),
    ('099f6e9c-cb20-5a64-8bc8-7d5eb3e51ef4', 'crystal-red-sss', 'Crystal Red', '0c979714-dfef-5d89-890c-b73df0dff46c', 'e159ea8b-d874-5443-87f4-989139c3001d', 'ad66974e-cee6-54d9-96b9-0e41653e0058', 'Top grade Crystal Red with mostly solid white bodies.', 'Our best Crystal Red selection. Keep in **mature, very stable soft-water tanks**.

**Water:** see the care parameters. Acclimatise slowly (drip method) over at least an hour.', NULL, 'ACTIVE', true, false, 8, 5, NULL, 1900, 1290, NULL, 'Top grade Crystal Red with mostly solid white bodies.', '2026-10-10 09:00:00+00'),
    ('17117550-9fe1-5836-9114-9a273b173077', 'tiger-shrimp', 'Tiger Shrimp', '2985a6cd-fbdd-5025-b1d7-050ba168e3c3', 'e159ea8b-d874-5443-87f4-989139c3001d', NULL, 'Striped, lively shrimp for soft to medium-hard water.', 'Tiger shrimp show **fine black stripes** on an amber body and are always on the move.

**Water:** see the care parameters. Acclimatise slowly (drip method) over at least an hour.', NULL, 'ACTIVE', true, false, 35, 5, NULL, 1900, 250, NULL, 'Striped, lively shrimp for soft to medium-hard water.', '2026-10-11 09:00:00+00'),
    ('034b0c84-a044-5acd-89e9-93e203f73542', 'amano-shrimp', 'Amano Shrimp', 'd1829f19-ad9e-5624-afa4-7713bfba1bd7', 'e159ea8b-d874-5443-87f4-989139c3001d', NULL, 'The best algae eater among shrimp – peaceful and long-lived.', 'Amano shrimp **eat many kinds of algae** and are welcome in almost every community tank.

**Water:** see the care parameters. Acclimatise slowly (drip method) over at least an hour.', NULL, 'ACTIVE', true, true, 80, 10, NULL, 1900, 290, NULL, 'The best algae eater among shrimp – peaceful and long-lived.', '2026-10-12 09:00:00+00'),
    ('90a6e709-77f9-58cf-92e5-7927b7c02c10', 'zebra-nerite-snail', 'Zebra Nerite Snail', '3280802e-3f61-5f45-873b-494c73ac69ad', '68a437de-a786-5ca4-83f6-21cc045fe9f1', NULL, 'Striped algae grazer that does not overpopulate the tank.', 'Zebra nerites keep glass and stones **clean of algae**. Their eggs do not hatch in fresh water.', NULL, 'ACTIVE', true, false, 40, 5, NULL, 1900, 350, NULL, 'Striped algae grazer that does not overpopulate the tank.', '2026-10-13 09:00:00+00'),
    ('1a7c9ead-d35f-53c6-83c9-dc76142fa0dc', 'red-ramshorn-snail', 'Red Ramshorn Snail', 'e4863203-a908-5879-8f72-996d58ecf524', '68a437de-a786-5ca4-83f6-21cc045fe9f1', NULL, 'Colourful clean-up crew for leftover food and soft algae.', 'Red ramshorn snails are **easy, colourful helpers**. Feed sparingly to keep their numbers in check.', NULL, 'ACTIVE', true, false, 100, 10, NULL, 1900, 690, NULL, 'Colourful clean-up crew for leftover food and soft algae.', '2026-10-14 09:00:00+00'),
    ('1bba97b5-33d3-52c4-adac-544e2c4316fd', 'orange-dwarf-crayfish', 'Orange Dwarf Crayfish', '0cf95a1b-2c62-58d8-a5e9-58529caebfce', 'f238f676-740b-51e2-a77e-2cc63ae3a9ed', NULL, 'Bright orange dwarf crayfish that leaves plants alone.', 'A **peaceful dwarf crayfish** for planted tanks. Offer caves and plenty of hiding places, especially when moulting.', NULL, 'ACTIVE', true, true, 6, 4, NULL, 1900, 1390, NULL, 'Bright orange dwarf crayfish that leaves plants alone.', '2026-10-15 09:00:00+00'),
    ('d395a235-9c45-5347-b00f-053fe5ded7a2', 'thai-micro-crab', 'Thai Micro Crab', 'af34cefd-5eb2-5fa8-a5be-65ecb24c14c8', '2ef12647-5cfb-51d1-ac94-de58518230fa', NULL, 'A tiny, fully aquatic crab for calm nano tanks.', 'Thai micro crabs **filter food from the water** and hide among roots and floating plants.', NULL, 'ACTIVE', true, false, 15, 5, NULL, 1900, 490, NULL, 'A tiny, fully aquatic crab for calm nano tanks.', '2026-10-16 09:00:00+00'),
    ('c72723a2-a44e-58c2-b76e-0b57235254c1', 'snow-white-draft', 'Snow White', '0c979714-dfef-5d89-890c-b73df0dff46c', 'e159ea8b-d874-5443-87f4-989139c3001d', NULL, 'Draft product – not visible in the shop.', 'Draft.', NULL, 'DRAFT', true, false, 10, 5, NULL, 1900, 990, NULL, 'Draft product – not visible in the shop.', NULL)
ON CONFLICT (id) DO UPDATE SET slug = EXCLUDED.slug, name = EXCLUDED.name, species_id = EXCLUDED.species_id, category_id = EXCLUDED.category_id, grade_id = EXCLUDED.grade_id, short_description = EXCLUDED.short_description, description = EXCLUDED.description, care_notes = EXCLUDED.care_notes, status = EXCLUDED.status, is_live_animal = EXCLUDED.is_live_animal, featured = EXCLUDED.featured, stock_units = EXCLUDED.stock_units, low_stock_threshold = EXCLUDED.low_stock_threshold, available_from = EXCLUDED.available_from, tax_rate_bps = EXCLUDED.tax_rate_bps, min_price_cents = EXCLUDED.min_price_cents, seo_title = EXCLUDED.seo_title, seo_description = EXCLUDED.seo_description, published_at = EXCLUDED.published_at;

INSERT INTO product_variants (id, product_id, sku, name, pack_size, price_cents, compare_at_price_cents, active, sort_order) VALUES
    ('5c395fdf-55ee-50d3-a5e0-0be344846403', 'c6f523c7-00f4-581f-ad2b-e6040df2536f', 'BLUE-DREAM-HIGH-1', '1 animal', 1, 390, NULL, true, 0),
    ('2ef6c35c-1b00-5f94-a911-01d4d4e71a79', 'c6f523c7-00f4-581f-ad2b-e6040df2536f', 'BLUE-DREAM-HIGH-10', '10 animals', 10, 3490, 3900, true, 1),
    ('17e78dff-c8fc-5d51-8d06-939fcf97ace1', '6d2fe5be-b74c-557b-8da2-5979709d1277', 'BLUE-DREAM-PREMIUM-1', '1 animal', 1, 590, NULL, true, 0),
    ('36fc3ee7-333a-5fa2-bd95-5060e2db0cd2', '6d2fe5be-b74c-557b-8da2-5979709d1277', 'BLUE-DREAM-PREMIUM-10', '10 animals', 10, 5200, NULL, true, 1),
    ('29fc396b-a6f4-59fd-92f9-fcf4ce4bd933', '0b1ce6d8-8996-5711-92b8-c19004d01224', 'RED-FIRE-STANDARD-1', '1 animal', 1, 190, NULL, true, 0),
    ('6a13b5fd-0cf3-5b27-b0df-b8d401d2b42a', '0b1ce6d8-8996-5711-92b8-c19004d01224', 'RED-FIRE-STANDARD-10', '10 animals', 10, 1690, NULL, true, 1),
    ('689c6aaf-898d-5516-ac49-258ba1708782', 'ae66fe41-0d19-5e1d-a8dd-d11bb4474669', 'BLOODY-MARY-PREMIU-1', '1 animal', 1, 450, NULL, true, 0),
    ('cf4851a6-0959-5984-b781-f6a34f2e2f9d', 'ae66fe41-0d19-5e1d-a8dd-d11bb4474669', 'BLOODY-MARY-PREMIU-10', '10 animals', 10, 3900, NULL, true, 1),
    ('8e3298bc-4e28-581d-a14e-d70d424975d2', 'f06d6250-947d-54e3-818c-d5c4af711710', 'YELLOW-FIRE-HIGH-1', '1 animal', 1, 290, NULL, true, 0),
    ('a15bd1d1-19be-5592-9473-bccf1b54df43', 'f06d6250-947d-54e3-818c-d5c4af711710', 'YELLOW-FIRE-HIGH-10', '10 animals', 10, 2500, NULL, true, 1),
    ('81e3ac37-21ad-57ff-9fe7-3e34df824e9a', 'f2459026-646a-5319-9ac4-82c5f1f0bd45', 'ORANGE-SAKURA-STAN-1', '1 animal', 1, 220, NULL, true, 0),
    ('ed842513-2036-549b-ae30-6681860603ce', 'f2459026-646a-5319-9ac4-82c5f1f0bd45', 'ORANGE-SAKURA-STAN-10', '10 animals', 10, 1950, NULL, true, 1),
    ('3044c92f-c2eb-53a8-a77f-b84efdc5b695', '1f898b15-185c-58fd-951b-dd0e4e2837e1', 'GREEN-JADE-HIGH-1', '1 animal', 1, 350, NULL, true, 0),
    ('64759944-6e42-544d-a45c-a0b6741595e5', '1f898b15-185c-58fd-951b-dd0e4e2837e1', 'GREEN-JADE-HIGH-10', '10 animals', 10, 3000, NULL, true, 1),
    ('4076d5d5-9473-5ee5-8e94-dd9dca9ef638', 'c610b173-3bdc-575e-953e-1e07d383b30a', 'BLACK-ROSE-PREMIUM-1', '1 animal', 1, 490, NULL, true, 0),
    ('e3e87c42-3362-5b1f-aeab-45e2cf63a05b', '4930bdf9-8aed-542c-8e83-6b8dbaa01dd7', 'CRYSTAL-RED-SS-1', '1 animal', 1, 690, NULL, true, 0),
    ('47eeb2bc-5797-519d-8ba0-9ca0b2b6b60d', '4930bdf9-8aed-542c-8e83-6b8dbaa01dd7', 'CRYSTAL-RED-SS-10', '10 animals', 10, 5900, NULL, true, 1),
    ('4b7649fa-9e45-5eb6-bdd4-cfbeb3703b7c', '099f6e9c-cb20-5a64-8bc8-7d5eb3e51ef4', 'CRYSTAL-RED-SSS-1', '1 animal', 1, 1290, NULL, true, 0),
    ('734d1d36-200b-53d4-9d2d-80a2ca771fc1', '099f6e9c-cb20-5a64-8bc8-7d5eb3e51ef4', 'CRYSTAL-RED-SSS-5', '5 animals', 5, 5900, NULL, true, 1),
    ('c2dd2615-1bd0-5520-a62e-07a7bb1313fe', '17117550-9fe1-5836-9114-9a273b173077', 'TIGER-SHRIMP-1', '1 animal', 1, 250, NULL, true, 0),
    ('5703ab45-a8ae-5114-9cce-9c11f61cba65', '17117550-9fe1-5836-9114-9a273b173077', 'TIGER-SHRIMP-10', '10 animals', 10, 2200, NULL, true, 1),
    ('30e6313f-d491-57fe-9d21-a50a90e28539', '034b0c84-a044-5acd-89e9-93e203f73542', 'AMANO-SHRIMP-1', '1 animal', 1, 290, NULL, true, 0),
    ('6024980a-7c69-578e-b238-f3bce6bad1e7', '034b0c84-a044-5acd-89e9-93e203f73542', 'AMANO-SHRIMP-10', '10 animals', 10, 2500, NULL, true, 1),
    ('1d01a9d5-32de-5171-90ed-b4d61948dd2e', '90a6e709-77f9-58cf-92e5-7927b7c02c10', 'ZEBRA-NERITE-SNAIL-1', '1 snail', 1, 350, NULL, true, 0),
    ('162beeef-c75e-57dd-96ef-3c1a6a25fbfa', '90a6e709-77f9-58cf-92e5-7927b7c02c10', 'ZEBRA-NERITE-SNAIL-5', '5 snails', 5, 1500, NULL, true, 1),
    ('f54b39ab-42fb-570d-8dcd-3f74152a0a26', '1a7c9ead-d35f-53c6-83c9-dc76142fa0dc', 'RED-RAMSHORN-SNAIL-5', '5 snails', 5, 690, NULL, true, 0),
    ('de1d0bf4-3f2f-5b64-a203-617bf4b9054a', '1a7c9ead-d35f-53c6-83c9-dc76142fa0dc', 'RED-RAMSHORN-SNAIL-10', '10 snails', 10, 1190, NULL, true, 1),
    ('3b9a9b94-9138-546f-a227-963d5d9b3393', '1bba97b5-33d3-52c4-adac-544e2c4316fd', 'ORANGE-DWARF-CRAYF-1', '1 animal', 1, 1390, NULL, true, 0),
    ('0430e5c3-f243-5dba-8672-673ba0b0b531', '1bba97b5-33d3-52c4-adac-544e2c4316fd', 'ORANGE-DWARF-CRAYF-2', 'Pair (1 male, 1 female)', 2, 2490, NULL, true, 1),
    ('dc51b5e5-5e86-5e89-96d0-98b14eba3071', 'd395a235-9c45-5347-b00f-053fe5ded7a2', 'THAI-MICRO-CRAB-1', '1 animal', 1, 490, NULL, true, 0),
    ('95662cf1-1996-5421-84b7-d8e5f6db7d23', 'd395a235-9c45-5347-b00f-053fe5ded7a2', 'THAI-MICRO-CRAB-5', '5 animals', 5, 2200, NULL, true, 1),
    ('e89a29ce-cafa-532f-8db2-a1241c67adc7', 'c72723a2-a44e-58c2-b76e-0b57235254c1', 'SNOW-WHITE-DRAFT-1', '1 animal', 1, 990, NULL, true, 0)
ON CONFLICT (id) DO UPDATE SET product_id = EXCLUDED.product_id, sku = EXCLUDED.sku, name = EXCLUDED.name, pack_size = EXCLUDED.pack_size, price_cents = EXCLUDED.price_cents, compare_at_price_cents = EXCLUDED.compare_at_price_cents, active = EXCLUDED.active, sort_order = EXCLUDED.sort_order;

INSERT INTO product_colours (product_id, colour_id) VALUES
    ('c6f523c7-00f4-581f-ad2b-e6040df2536f', 'bfc220d1-4841-5f39-b15b-63ee579b2feb'),
    ('6d2fe5be-b74c-557b-8da2-5979709d1277', 'bfc220d1-4841-5f39-b15b-63ee579b2feb'),
    ('0b1ce6d8-8996-5711-92b8-c19004d01224', '5cafffbf-320a-5d2e-b5de-9adc397aa9e5'),
    ('ae66fe41-0d19-5e1d-a8dd-d11bb4474669', '5cafffbf-320a-5d2e-b5de-9adc397aa9e5'),
    ('f06d6250-947d-54e3-818c-d5c4af711710', 'fa682888-85ce-5383-922f-d1fc9c321e11'),
    ('f2459026-646a-5319-9ac4-82c5f1f0bd45', '3ed9e794-67c2-5d49-9c05-1d23bd84b11d'),
    ('1f898b15-185c-58fd-951b-dd0e4e2837e1', '6dc50c21-47d8-5a0e-a1cb-2600103dc04c'),
    ('c610b173-3bdc-575e-953e-1e07d383b30a', '6654a3ed-ce55-5e65-832a-b948543eea9e'),
    ('4930bdf9-8aed-542c-8e83-6b8dbaa01dd7', '5cafffbf-320a-5d2e-b5de-9adc397aa9e5'),
    ('4930bdf9-8aed-542c-8e83-6b8dbaa01dd7', '83a8e974-8e17-541a-a536-9b94a2448461'),
    ('099f6e9c-cb20-5a64-8bc8-7d5eb3e51ef4', '5cafffbf-320a-5d2e-b5de-9adc397aa9e5'),
    ('099f6e9c-cb20-5a64-8bc8-7d5eb3e51ef4', '83a8e974-8e17-541a-a536-9b94a2448461'),
    ('17117550-9fe1-5836-9114-9a273b173077', '6654a3ed-ce55-5e65-832a-b948543eea9e'),
    ('17117550-9fe1-5836-9114-9a273b173077', 'beeb9a91-85ef-58d9-97e1-963e1ac7286a'),
    ('034b0c84-a044-5acd-89e9-93e203f73542', 'd436940a-155e-5b88-91b6-9850e9c2b2fb'),
    ('90a6e709-77f9-58cf-92e5-7927b7c02c10', 'fa682888-85ce-5383-922f-d1fc9c321e11'),
    ('90a6e709-77f9-58cf-92e5-7927b7c02c10', '6654a3ed-ce55-5e65-832a-b948543eea9e'),
    ('1a7c9ead-d35f-53c6-83c9-dc76142fa0dc', '5cafffbf-320a-5d2e-b5de-9adc397aa9e5'),
    ('1bba97b5-33d3-52c4-adac-544e2c4316fd', '3ed9e794-67c2-5d49-9c05-1d23bd84b11d'),
    ('d395a235-9c45-5347-b00f-053fe5ded7a2', 'beeb9a91-85ef-58d9-97e1-963e1ac7286a'),
    ('c72723a2-a44e-58c2-b76e-0b57235254c1', '83a8e974-8e17-541a-a536-9b94a2448461')
ON CONFLICT (product_id, colour_id) DO NOTHING;

INSERT INTO stock_movements (id, product_id, delta, stock_after, reason, note) VALUES
    ('2a6589d2-6cb9-59ab-a8aa-680611b2b9c3', 'c6f523c7-00f4-581f-ad2b-e6040df2536f', 48, 48, 'INITIAL', 'Dev seed'),
    ('ecd47d1c-a274-5279-b192-50d39a00e6aa', '6d2fe5be-b74c-557b-8da2-5979709d1277', 6, 6, 'INITIAL', 'Dev seed'),
    ('3dee7375-c8da-5842-82f3-dc07564df505', '0b1ce6d8-8996-5711-92b8-c19004d01224', 120, 120, 'INITIAL', 'Dev seed'),
    ('4cc8ce91-6d79-50c7-9b4b-146b2b88a2a7', 'ae66fe41-0d19-5e1d-a8dd-d11bb4474669', 30, 30, 'INITIAL', 'Dev seed'),
    ('0a29049b-7b82-5676-9e80-46fa8ef5add9', 'f2459026-646a-5319-9ac4-82c5f1f0bd45', 40, 40, 'INITIAL', 'Dev seed'),
    ('ec43b8d8-1522-5679-9a85-e91fd2c63c5f', '1f898b15-185c-58fd-951b-dd0e4e2837e1', 25, 25, 'INITIAL', 'Dev seed'),
    ('80240df9-fa2c-5554-ae43-8327215b9e19', '4930bdf9-8aed-542c-8e83-6b8dbaa01dd7', 22, 22, 'INITIAL', 'Dev seed'),
    ('b24a9113-1e24-5bcd-8f2b-f1cb4510097c', '099f6e9c-cb20-5a64-8bc8-7d5eb3e51ef4', 8, 8, 'INITIAL', 'Dev seed'),
    ('a73e1f68-7d88-5cad-9742-40c757ad6ad3', '17117550-9fe1-5836-9114-9a273b173077', 35, 35, 'INITIAL', 'Dev seed'),
    ('8abc4c6a-5a6d-5cf7-9229-fc6bd9cfbfda', '034b0c84-a044-5acd-89e9-93e203f73542', 80, 80, 'INITIAL', 'Dev seed'),
    ('795c094a-b779-5b3e-ac69-1f99d5a44298', '90a6e709-77f9-58cf-92e5-7927b7c02c10', 40, 40, 'INITIAL', 'Dev seed'),
    ('2a18e3bb-de72-5325-87f1-4d86b090ec28', '1a7c9ead-d35f-53c6-83c9-dc76142fa0dc', 100, 100, 'INITIAL', 'Dev seed'),
    ('8025b5e6-edc4-5e49-881b-3c10a2a67d8b', '1bba97b5-33d3-52c4-adac-544e2c4316fd', 6, 6, 'INITIAL', 'Dev seed'),
    ('7c772e77-d95d-55b5-94d9-822603da0ca8', 'd395a235-9c45-5347-b00f-053fe5ded7a2', 15, 15, 'INITIAL', 'Dev seed'),
    ('c4f408a3-e0b2-5c35-8e12-ac2e80135046', 'c72723a2-a44e-58c2-b76e-0b57235254c1', 10, 10, 'INITIAL', 'Dev seed')
ON CONFLICT (id) DO UPDATE SET product_id = EXCLUDED.product_id, delta = EXCLUDED.delta, stock_after = EXCLUDED.stock_after, reason = EXCLUDED.reason, note = EXCLUDED.note;
