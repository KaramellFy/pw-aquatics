package com.pwaquatics.shop.payment.domain;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.common.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One online payment of an order, as known from the provider. Holds the provider's
 * reference, the amount and its state, never card data.
 *
 * <p>Provider events can arrive late, twice or out of order, so every change checks the
 * current state: a payment that has succeeded is never turned back into a failed one.
 */
@Entity
@Table(name = "payments")
public class Payment extends AbstractEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PaymentMethod provider;

    @Column(name = "provider_reference", unique = true, updatable = false)
    private String providerReference;

    /** PayPal: the id of the capture that took the money (refunds name only this one). */
    @Column(name = "capture_reference", unique = true, length = 100)
    private String captureReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentState status;

    @Column(name = "amount_cents", nullable = false, updatable = false)
    private long amountCents;

    @Column(name = "refunded_cents", nullable = false)
    private long refundedCents;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "method_detail", length = 50)
    private String methodDetail;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Payment() {}

    /** A Stripe PaymentIntent created for the stored total of an order. */
    public static Payment stripe(UUID orderId, String paymentIntentId, Money amount, Instant now) {
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.provider = PaymentMethod.STRIPE;
        payment.providerReference = paymentIntentId;
        payment.status = PaymentState.CREATED;
        payment.amountCents = amount.amount();
        payment.currency = amount.currency();
        payment.createdAt = now;
        payment.updatedAt = now;
        return payment;
    }

    /** A PayPal order created for the stored total of an order. */
    public static Payment paypal(UUID orderId, String paypalOrderId, Money amount, Instant now) {
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.provider = PaymentMethod.PAYPAL;
        payment.providerReference = paypalOrderId;
        payment.status = PaymentState.CREATED;
        payment.amountCents = amount.amount();
        payment.currency = amount.currency();
        payment.createdAt = now;
        payment.updatedAt = now;
        return payment;
    }

    /**
     * Records the provider's capture of this payment (PayPal). A payment has one capture; a
     * different one is refused.
     *
     * @throws IllegalArgumentException if another capture was recorded before
     */
    public void capturedAs(String captureId, Instant now) {
        if (captureReference == null) {
            captureReference = captureId;
            updatedAt = now;
        } else if (!captureReference.equals(captureId)) {
            throw new IllegalArgumentException("The payment already has another capture");
        }
    }

    /** The provider is processing the payment (e.g. waiting for the bank). */
    public void processing(Instant now) {
        if (status == PaymentState.CREATED || status == PaymentState.FAILED) {
            status = PaymentState.PENDING;
            updatedAt = now;
        }
    }

    /**
     * The money has arrived.
     *
     * @param received the amount the provider reports as received
     * @return true the first time; false for a repeated or late report
     * @throws IllegalArgumentException if the provider reports a different amount or currency
     */
    public boolean succeeded(Money received, String methodDetail, Instant now) {
        if (received.amount() != amountCents || !received.currency().equalsIgnoreCase(currency)) {
            throw new IllegalArgumentException("Received " + received + " for a payment of " + amount());
        }
        if (status.isSettled()) {
            return false;
        }
        status = PaymentState.SUCCEEDED;
        this.methodDetail = methodDetail;
        failureCode = null;
        updatedAt = now;
        return true;
    }

    /** An attempt was declined. Ignored once the payment has succeeded. */
    public void failed(String failureCode, Instant now) {
        if (status.isSettled() || status == PaymentState.CANCELLED) {
            return;
        }
        status = PaymentState.FAILED;
        this.failureCode = failureCode;
        updatedAt = now;
    }

    /** The payment was cancelled at the provider. Ignored once money has arrived. */
    public void cancelled(Instant now) {
        if (!status.isSettled()) {
            status = PaymentState.CANCELLED;
            updatedAt = now;
        }
    }

    /**
     * Records the total amount refunded so far, as the provider reports it (refunds from the
     * admin order page and from the provider's dashboard). The total only grows here; a refund
     * that fails afterwards is taken back with {@link #refundFailed}.
     *
     * @return true if the refunded amount changed
     */
    public boolean refunded(long totalRefundedCents, Instant now) {
        if (!status.isSettled() || totalRefundedCents <= refundedCents) {
            return false;
        }
        if (totalRefundedCents > amountCents) {
            throw new IllegalArgumentException("Refunded more than was paid");
        }
        refundedCents = totalRefundedCents;
        settleRefundStatus(now);
        return true;
    }

    /**
     * The provider accepted a refund the shop asked for: it counts as refunded from now on, as
     * the provider counts it (also while the provider is still paying it out).
     *
     * @throws IllegalArgumentException if the amount is not positive, in another currency or
     *     more than is left to refund
     * @throws IllegalStateException if no money has arrived
     */
    public void refundAccepted(Money amount, Instant now) {
        if (!status.isSettled()) {
            throw new IllegalStateException("Only a payment that has arrived can be refunded");
        }
        if (amount.amount() <= 0 || amount.amount() > amountCents - refundedCents
                || !amount.currency().equalsIgnoreCase(currency)) {
            throw new IllegalArgumentException("A refund of " + amount + " does not fit a payment of " + amount());
        }
        refundedCents += amount.amount();
        settleRefundStatus(now);
    }

    /**
     * A refund the provider had accepted failed after all (e.g. the card was closed): the money
     * stays with the shop, so it no longer counts as refunded.
     *
     * @return true if the refunded amount changed
     */
    public boolean refundFailed(Money amount, Instant now) {
        if (!status.isSettled() || amount.amount() <= 0 || refundedCents == 0) {
            return false;
        }
        refundedCents = Math.max(0, refundedCents - amount.amount());
        settleRefundStatus(now);
        return true;
    }

    /** What may still be refunded: nothing until the money has arrived. */
    public Money refundable() {
        return new Money(status.isSettled() ? amountCents - refundedCents : 0, currency);
    }

    private void settleRefundStatus(Instant now) {
        if (refundedCents == 0) {
            status = PaymentState.SUCCEEDED;
        } else {
            status = refundedCents == amountCents ? PaymentState.REFUNDED : PaymentState.PARTIALLY_REFUNDED;
        }
        updatedAt = now;
    }

    public boolean isFullyRefunded() {
        return status == PaymentState.REFUNDED;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public PaymentMethod getProvider() {
        return provider;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getCaptureReference() {
        return captureReference;
    }

    public PaymentState getStatus() {
        return status;
    }

    public Money amount() {
        return new Money(amountCents, currency);
    }

    public Money refunded() {
        return new Money(refundedCents, currency);
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getMethodDetail() {
        return methodDetail;
    }
}
