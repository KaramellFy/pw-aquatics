package com.pwaquatics.shop.payment.application;

import com.pwaquatics.shop.common.money.Money;
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

    /**
     * Whether the (locked) order still waits for its online payment: not paid, not cancelled
     * and before its payment deadline. PayPal approvals reported by webhook are captured only then.
     */
    boolean isAwaitingPayment(UUID orderId);

    /** The full amount has arrived. Called once per payment. */
    void paymentSucceeded(PaymentSucceeded payment);

    /**
     * The money refunded changed: paid back in full or in part (from the admin order page, the
     * Stripe Dashboard or PayPal), or less than before because a refund failed.
     */
    void paymentRefunded(PaymentRefunded refund);

    /** A refund the provider had accepted failed afterwards; the owner has to pay it back another way. */
    void refundFailed(UUID orderId, Money amount);
}
