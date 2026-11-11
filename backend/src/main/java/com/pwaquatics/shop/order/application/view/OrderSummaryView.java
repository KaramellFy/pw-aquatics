package com.pwaquatics.shop.order.application.view;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.order.domain.OrderStatus;
import com.pwaquatics.shop.order.domain.PaymentStatus;
import java.time.Instant;
import java.util.List;

/**
 * One row of the order history.
 *
 * @param itemCount number of packs ordered
 * @param productNames the ordered products, in order of the order lines
 */
public record OrderSummaryView(
        String orderNumber,
        OrderStatus status,
        PaymentStatus paymentStatus,
        Instant placedAt,
        Money total,
        int itemCount,
        List<String> productNames) {}
