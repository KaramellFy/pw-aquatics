package com.pwaquatics.shop.catalog.domain;

/** Derived availability of a product (docs/DATABASE.md §3.2). Never stored. */
public enum Availability {
    IN_STOCK,
    LOW_STOCK,
    COMING_SOON,
    OUT_OF_STOCK;

    public boolean purchasable() {
        return this == IN_STOCK || this == LOW_STOCK;
    }
}
