package com.pwaquatics.shop.payment.application;

import com.pwaquatics.shop.payment.domain.PaymentRefunded;
import com.pwaquatics.shop.payment.domain.PaymentSucceeded;
import java.util.UUID;

/**
 * What the payment module needs from the orders it collects money for. Implemented by the
 * order module, so the payment module never depends on it. All methods run inside the
 * caller's transaction.
 */
public interface PayableOrders {

    /**
     * Locks the order for the current transaction. Called before a payment record is locked,
     * so every path takes the two locks in the same order (order, then payment) and two
     * transactions never wait for each other.
     */
    void lock(UUID orderId);

    /** The full amount has arrived. Called once per payment. */
    void paymentSucceeded(PaymentSucceeded payment);

    /** Money was paid back (in full or in part), e.g. from the Stripe Dashboard. */
    void paymentRefunded(PaymentRefunded refund);
}
