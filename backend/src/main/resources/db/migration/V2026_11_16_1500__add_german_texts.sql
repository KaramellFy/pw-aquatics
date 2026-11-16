-- German versions of the texts the owner writes (docs/adr/012-language-in-links.md,
-- docs/DATABASE.md §1). Each <column>_de sits next to the English column; NULL means
-- "not translated yet" and the English text is shown instead.

ALTER TABLE products
    ADD COLUMN name_de              varchar(150),
    ADD COLUMN short_description_de varchar(300),
    ADD COLUMN description_de       text,
    ADD COLUMN care_notes_de        text,
    ADD COLUMN seo_title_de         varchar(160),
    ADD COLUMN seo_description_de   varchar(320);
-- Customers search in either language.
CREATE INDEX idx_products_name_de_trgm ON products USING gin (name_de gin_trgm_ops);

ALTER TABLE product_variants ADD COLUMN name_de varchar(100);
ALTER TABLE product_images   ADD COLUMN alt_text_de varchar(255);

ALTER TABLE categories
    ADD COLUMN name_de        varchar(100),
    ADD COLUMN description_de text;

ALTER TABLE colours ADD COLUMN name_de varchar(60);

ALTER TABLE grades
    ADD COLUMN name_de        varchar(60),
    ADD COLUMN description_de text;

ALTER TABLE species
    ADD COLUMN common_name_de      varchar(150),
    ADD COLUMN description_de      text,
    ADD COLUMN origin_de           varchar(150),
    ADD COLUMN restriction_note_de text;

ALTER TABLE shipping_methods
    ADD COLUMN name_de        varchar(100),
    ADD COLUMN description_de varchar(500);

ALTER TABLE gallery_images
    ADD COLUMN title_de    varchar(150),
    ADD COLUMN caption_de  varchar(500),
    ADD COLUMN alt_text_de varchar(255);

ALTER TABLE coupons ADD COLUMN description_de varchar(255);
