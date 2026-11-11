package com.pwaquatics.shop.catalog.domain;

/** Why a product cannot be published (yet). The names are the API error codes. */
public enum PublishProblem {
    /** No active pack size to sell. */
    NO_ACTIVE_VARIANT,
    /** No photo; the shop shows products only with at least one. */
    NO_IMAGE,
    /** The species may not be traded. */
    SPECIES_RESTRICTED
}
