package com.pwaquatics.shop.order.domain;

import com.pwaquatics.shop.payment.domain.PaymentMethod;
import com.pwaquatics.shop.pricing.domain.Discount;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Everything needed to create an order. Prices and the shipping price come from the server's
 * own data; the order calculates its totals from them.
 *
 * @param shippingCents the shipping method's price (a free-shipping coupon waives it)
 * @param paymentDueAt when an unpaid order expires
 * @param discount the terms of the coupon that applies to this order, or null
 * @param couponCode the coupon's code (snapshot), or null
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
        Instant paymentDueAt,
        Discount discount,
        String couponCode) {

    public NewOrder {
        if ((discount == null) != (couponCode == null)) {
            throw new IllegalArgumentException("A discount needs its coupon code and vice versa");
        }
        items = List.copyOf(items);
    }

    /** An order without a coupon. */
    public NewOrder(
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
            Instant paymentDueAt) {
        this(orderNumber, userId, customerEmail, customerLocale, paymentMethod, items, shippingCents,
                shippingMethodCode, shippingMethodName, shippingCarrier, plannedDispatchDate, shippingAddress,
                billingAddress, customerNote, idempotencyKey, paymentDueAt, null, null);
    }
}
