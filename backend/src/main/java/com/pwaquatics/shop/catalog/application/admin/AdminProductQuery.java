package com.pwaquatics.shop.catalog.application.admin;

import com.pwaquatics.shop.catalog.domain.ProductStatus;
import java.util.UUID;

/**
 * Filters of the admin product list. All optional.
 *
 * @param q text in the name, web address or a SKU
 * @param page zero-based
 */
public record AdminProductQuery(
        String q, ProductStatus status, UUID categoryId, StockFilter stock, AdminProductSort sort, int page, int size) {

    /** Which stock levels to list. */
    public enum StockFilter {
        ALL,
        /** Some animals left, at or below the product's low-stock threshold. */
        LOW_STOCK,
        /** No animals left. */
        OUT_OF_STOCK
    }

    public enum AdminProductSort {
        UPDATED_DESC,
        NAME_ASC,
        STOCK_ASC
    }
}
