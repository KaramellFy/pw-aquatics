-- Phase 7: fulfilment (docs/DATABASE.md §3.7).

-- Parcels handed to a carrier. An order usually has one; a second one is possible (split parcel).
CREATE TABLE shipments (
    id               uuid PRIMARY KEY,
    order_id         uuid         NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    carrier          varchar(50)  NOT NULL,
    tracking_number  varchar(100) NOT NULL,
    tracking_url     varchar(500),           -- built from the carrier's URL template
    shipped_at       timestamptz  NOT NULL,
    delivered_at     timestamptz,
    created_by       uuid         REFERENCES users(id) ON DELETE SET NULL,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_shipments_order      ON shipments (order_id);
CREATE INDEX idx_shipments_tracking   ON shipments (tracking_number);
CREATE INDEX idx_shipments_created_by ON shipments (created_by);

-- Internal notes of the shop on an order; never shown to the customer.
CREATE TABLE order_notes (
    id          uuid PRIMARY KEY,
    order_id    uuid          NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    author_id   uuid          REFERENCES users(id) ON DELETE SET NULL,
    body        varchar(2000) NOT NULL,
    created_at  timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX idx_order_notes_order  ON order_notes (order_id, created_at);
CREATE INDEX idx_order_notes_author ON order_notes (author_id);
