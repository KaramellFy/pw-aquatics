package com.pwaquatics.shop.order.domain;

import com.pwaquatics.shop.payment.domain.PaymentMethod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Everything needed to create an order. Prices and the shipping price come from the server's
 * own data; the order calculates its totals from them.
 *
 * @param paymentDueAt when an unpaid order expires
 */
public record NewOrder(
        String orderNumber,
        UUID userId,
        String customerEmail,
        String customerLocale,
        PaymentMethod paymentMethod,
        List<NewOrderItem> items,
        long shippingCents,
        String shippingMethodCode,
        String shippingMethodName,
        String shippingCarrier,
        LocalDate plannedDispatchDate,
        OrderAddress shippingAddress,
        OrderAddress billingAddress,
        String customerNote,
        String idempotencyKey,
        Instant paymentDueAt) {}
