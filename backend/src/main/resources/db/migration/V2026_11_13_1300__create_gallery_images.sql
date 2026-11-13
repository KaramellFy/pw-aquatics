-- Phase 7: gallery managed in the admin area (docs/DATABASE.md §3.8), and optimistic locking
-- for shipping methods now that the owner edits them.

CREATE TABLE gallery_images (
    id           uuid PRIMARY KEY,
    -- Folder of the WebP renditions (<storage_key>/<width>.webp), see media.ImageService.
    storage_key  varchar(255) NOT NULL UNIQUE,
    title        varchar(150),
    caption      varchar(500),
    alt_text     varchar(255) NOT NULL,
    width        int          NOT NULL CHECK (width > 0),
    height       int          NOT NULL CHECK (height > 0),
    sort_order   int          NOT NULL DEFAULT 0,
    published    boolean      NOT NULL DEFAULT false,
    created_by   uuid         REFERENCES users(id) ON DELETE SET NULL,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    version      bigint       NOT NULL DEFAULT 0
);
CREATE INDEX idx_gallery_images_published ON gallery_images (published, sort_order);
CREATE INDEX idx_gallery_images_created_by ON gallery_images (created_by);

ALTER TABLE shipping_methods ADD COLUMN version bigint NOT NULL DEFAULT 0;
