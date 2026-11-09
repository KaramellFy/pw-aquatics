-- Catalog and inventory ledger (docs/DATABASE.md §3.2, §3.3).
-- Foreign keys from stock_movements to orders and users are added by the phases
-- that create those tables (5 and 3).

CREATE TABLE categories (
    id          uuid PRIMARY KEY,
    parent_id   uuid         REFERENCES categories(id) ON DELETE RESTRICT,
    name        varchar(100) NOT NULL,
    slug        varchar(120) NOT NULL UNIQUE,
    description text,
    image_key   varchar(255),
    sort_order  int          NOT NULL DEFAULT 0,
    active      boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_categories_parent ON categories (parent_id);

CREATE TABLE species (
    id                uuid PRIMARY KEY,
    scientific_name   varchar(150) NOT NULL UNIQUE,
    common_name       varchar(150),
    slug              varchar(170) NOT NULL UNIQUE,
    description       text,
    origin            varchar(150),
    temp_min_c        numeric(4,1),
    temp_max_c        numeric(4,1),
    ph_min            numeric(3,1),
    ph_max            numeric(3,1),
    gh_min            smallint,
    gh_max            smallint,
    kh_min            smallint,
    kh_max            smallint,
    tds_min           smallint,
    tds_max           smallint,
    adult_size_mm     smallint,
    lifespan_months   smallint,
    difficulty        varchar(20) CHECK (difficulty IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    -- Legal guard: e.g. species on the EU list of invasive alien species
    trade_restricted  boolean      NOT NULL DEFAULT false,
    restriction_note  text,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    CHECK (temp_min_c IS NULL OR temp_max_c IS NULL OR temp_min_c <= temp_max_c),
    CHECK (ph_min IS NULL OR ph_max IS NULL OR ph_min <= ph_max)
);

CREATE TABLE colours (
    id          uuid PRIMARY KEY,
    name        varchar(60) NOT NULL,
    slug        varchar(80) NOT NULL UNIQUE,
    hex_code    char(7) CHECK (hex_code ~ '^#[0-9A-Fa-f]{6}$'),
    sort_order  int         NOT NULL DEFAULT 0
);

CREATE TABLE grades (
    id          uuid PRIMARY KEY,
    name        varchar(60) NOT NULL,
    slug        varchar(80) NOT NULL UNIQUE,
    scale       varchar(50),
    description text,
    rank        smallint    NOT NULL DEFAULT 0,
    sort_order  int         NOT NULL DEFAULT 0
);

CREATE TABLE products (
    id                   uuid PRIMARY KEY,
    slug                 varchar(180) NOT NULL UNIQUE,
    name                 varchar(150) NOT NULL,
    species_id           uuid         REFERENCES species(id)    ON DELETE RESTRICT,
    category_id          uuid         NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    grade_id             uuid         REFERENCES grades(id)     ON DELETE RESTRICT,
    short_description    varchar(300),
    description          text,
    care_notes           text,
    status               varchar(20)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    is_live_animal       boolean      NOT NULL DEFAULT true,
    featured             boolean      NOT NULL DEFAULT false,
    stock_units          int          NOT NULL DEFAULT 0 CHECK (stock_units >= 0),
    low_stock_threshold  int          NOT NULL DEFAULT 5 CHECK (low_stock_threshold >= 0),
    available_from       date,
    tax_rate_bps         int          NOT NULL DEFAULT 1900 CHECK (tax_rate_bps BETWEEN 0 AND 10000),
    -- Cheapest active variant; maintained by the service layer for fast filtering and sorting.
    min_price_cents      bigint       CHECK (min_price_cents >= 0),
    seo_title            varchar(160),
    seo_description      varchar(320),
    published_at         timestamptz,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    version              bigint       NOT NULL DEFAULT 0
);
CREATE INDEX idx_products_status_category ON products (status, category_id);
CREATE INDEX idx_products_species         ON products (species_id);
CREATE INDEX idx_products_grade           ON products (grade_id);
CREATE INDEX idx_products_min_price       ON products (min_price_cents);
CREATE INDEX idx_products_featured        ON products (featured) WHERE featured;
CREATE INDEX idx_products_name_trgm       ON products USING gin (name gin_trgm_ops);

CREATE TABLE product_colours (
    product_id  uuid NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    colour_id   uuid NOT NULL REFERENCES colours(id)  ON DELETE RESTRICT,
    PRIMARY KEY (product_id, colour_id)
);
CREATE INDEX idx_product_colours_colour ON product_colours (colour_id);

-- A variant is a pack size with its own price, e.g. "1 animal" or "10 animals".
CREATE TABLE product_variants (
    id                     uuid PRIMARY KEY,
    product_id             uuid         NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sku                    varchar(64)  NOT NULL UNIQUE,
    name                   varchar(100) NOT NULL,
    pack_size              int          NOT NULL DEFAULT 1 CHECK (pack_size > 0),
    price_cents            bigint       NOT NULL CHECK (price_cents >= 0),
    compare_at_price_cents bigint       CHECK (compare_at_price_cents IS NULL OR compare_at_price_cents > price_cents),
    active                 boolean      NOT NULL DEFAULT true,
    sort_order             int          NOT NULL DEFAULT 0,
    created_at             timestamptz  NOT NULL DEFAULT now(),
    updated_at             timestamptz  NOT NULL DEFAULT now(),
    version                bigint       NOT NULL DEFAULT 0
);
CREATE INDEX idx_product_variants_product ON product_variants (product_id);

CREATE TABLE product_images (
    id          uuid PRIMARY KEY,
    product_id  uuid         NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    storage_key varchar(255) NOT NULL,
    alt_text    varchar(255) NOT NULL,
    width       int          NOT NULL CHECK (width > 0),
    height      int          NOT NULL CHECK (height > 0),
    sort_order  int          NOT NULL DEFAULT 0,
    created_at  timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_product_images_product ON product_images (product_id, sort_order);

-- Append-only inventory ledger. products.stock_units is the current balance.
CREATE TABLE stock_movements (
    id           uuid PRIMARY KEY,
    product_id   uuid        NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    delta        int         NOT NULL CHECK (delta <> 0),
    stock_after  int         NOT NULL CHECK (stock_after >= 0),
    reason       varchar(30) NOT NULL CHECK (reason IN (
                     'INITIAL', 'RESTOCK', 'ORDER_PLACED', 'ORDER_CANCELLED',
                     'MANUAL_ADJUSTMENT', 'LOSS', 'RETURN')),
    order_id     uuid,
    note         varchar(500),
    created_by   uuid,
    created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_stock_movements_product ON stock_movements (product_id, created_at DESC);
