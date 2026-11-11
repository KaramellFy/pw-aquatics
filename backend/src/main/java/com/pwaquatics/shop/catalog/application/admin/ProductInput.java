package com.pwaquatics.shop.catalog.application.admin;

import com.pwaquatics.shop.catalog.domain.ProductDetails;
import java.util.List;
import java.util.UUID;

/** A product's details and taxonomy links as entered in the admin editor. */
public record ProductInput(
        ProductDetails details, UUID categoryId, UUID speciesId, UUID gradeId, List<UUID> colourIds) {}
