package com.pwaquatics.shop.admin.application.view;

import com.pwaquatics.shop.common.money.Money;
import java.util.List;

/**
 * What needs attention right now (not tied to a period).
 *
 * @param awaitingPaymentOrders orders waiting for a bank transfer
 * @param awaitingPayment their total
 * @param ordersToShip paid orders not yet shipped
 * @param liveProducts published products
 * @param lowStockProducts published products at or below their threshold but not sold out
 * @param soldOutProducts published products with no stock
 * @param recentOrders the latest orders, newest first
 * @param lowStock published products running out, emptiest first
 */
public record DashboardView(
        long awaitingPaymentOrders,
        Money awaitingPayment,
        long ordersToShip,
        long liveProducts,
        long lowStockProducts,
        long soldOutProducts,
        List<RecentOrderView> recentOrders,
        List<LowStockProductView> lowStock) {}
