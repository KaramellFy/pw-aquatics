package com.pwaquatics.shop.admin.application.view;

import com.pwaquatics.shop.inventory.domain.StockMovementReason;

/**
 * Net change of stock for one ledger reason in the period.
 *
 * @param units animals added (positive) or removed (negative)
 */
public record StockReasonView(StockMovementReason reason, long units) {}
