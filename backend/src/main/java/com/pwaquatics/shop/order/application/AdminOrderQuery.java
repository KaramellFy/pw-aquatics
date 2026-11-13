package com.pwaquatics.shop.order.application;

import com.pwaquatics.shop.order.domain.OrderStatus;
import com.pwaquatics.shop.order.domain.PaymentStatus;
import java.time.LocalDate;
import java.util.Set;

/**
 * Filters of the admin order list. All optional.
 *
 * @param q part of the order number, email address or a name on the order
 * @param statuses any of these (empty: all)
 * @param from first day of placing, in the shop's time zone
 * @param to last day of placing, in the shop's time zone
 * @param oldestFirst e.g. for the orders still to be shipped
 */
public record AdminOrderQuery(
        String q,
        Set<OrderStatus> statuses,
        PaymentStatus paymentStatus,
        LocalDate from,
        LocalDate to,
        boolean oldestFirst,
        int page,
        int size) {}
