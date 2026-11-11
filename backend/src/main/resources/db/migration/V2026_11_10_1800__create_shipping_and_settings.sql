-- Phase 5: shipping methods and shop-wide settings (docs/DATABASE.md §3.4).

CREATE TABLE shipping_methods (
    id                            uuid PRIMARY KEY,
    code                          varchar(50)  NOT NULL UNIQUE,   -- e.g. 'EXPRESS_LIVE'
    name                          varchar(100) NOT NULL,
    description                   varchar(500),
    carrier                       varchar(50),
    price_cents                   bigint       NOT NULL CHECK (price_cents >= 0),
    -- Orders with a goods value of at least this amount ship for free.
    free_shipping_threshold_cents bigint       CHECK (free_shipping_threshold_cents > 0),
    live_animals_allowed          boolean      NOT NULL DEFAULT true,
    -- ISO weekdays on which parcels are handed to the carrier (1 = Monday ... 7 = Sunday).
    dispatch_weekdays             smallint[]   NOT NULL DEFAULT '{1,2,3}'
        CHECK (cardinality(dispatch_weekdays) > 0
               AND dispatch_weekdays <@ ARRAY[1, 2, 3, 4, 5, 6, 7]::smallint[]),
    estimated_days_min            smallint     CHECK (estimated_days_min >= 0),
    estimated_days_max            smallint,
    active                        boolean      NOT NULL DEFAULT true,
    sort_order                    int          NOT NULL DEFAULT 0,
    created_at                    timestamptz  NOT NULL DEFAULT now(),
    updated_at                    timestamptz  NOT NULL DEFAULT now(),
    CHECK (estimated_days_max IS NULL OR estimated_days_min IS NULL OR estimated_days_max >= estimated_days_min)
);

-- Countries a method delivers to (ISO 3166-1 alpha-2).
CREATE TABLE shipping_method_countries (
    shipping_method_id uuid    NOT NULL REFERENCES shipping_methods(id) ON DELETE CASCADE,
    country_code       char(2) NOT NULL CHECK (country_code ~ '^[A-Z]{2}$'),
    PRIMARY KEY (shipping_method_id, country_code)
);
CREATE INDEX idx_shipping_method_countries_country ON shipping_method_countries (country_code);

-- Small key/value store for shop-wide switches, e.g.
-- 'shipping.pause' = {"active": true, "message": "Heat wave", "until": "2027-08-12"}
CREATE TABLE shop_settings (
    key         varchar(100) PRIMARY KEY,
    value       jsonb        NOT NULL,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  uuid         REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_shop_settings_updated_by ON shop_settings (updated_by);
