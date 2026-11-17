package com.pwaquatics.shop.order.application.view;

/**
 * The result of confirming a PayPal payment the customer approved (docs/API.md §2.7).
 *
 * @param status {@code PAID} (the order is paid), {@code PENDING} (PayPal still checks the
 *     payment; an email follows once it is through) or {@code DECLINED} (nothing was taken; the
 *     customer may choose another way to pay in PayPal's window)
 */
public record PayPalCaptureView(String orderNumber, Status status) {

    public enum Status {
        PAID,
        PENDING,
        DECLINED
    }
}
