package com.pwaquatics.shop.admin.application.view;

import java.util.UUID;

/** A published product at or below its low-stock threshold. */
public record LowStockProductView(UUID id, String name, int stockUnits, int lowStockThreshold) {}
