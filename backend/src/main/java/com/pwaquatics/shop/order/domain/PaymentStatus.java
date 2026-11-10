package com.pwaquatics.shop.order.domain;

/** Whether the order has been paid; kept apart from {@link OrderStatus} so refunds do not distort fulfilment. */
public enum PaymentStatus {
    UNPAID,
    PAID,
    PARTIALLY_REFUNDED,
    REFUNDED,
    FAILED
}
