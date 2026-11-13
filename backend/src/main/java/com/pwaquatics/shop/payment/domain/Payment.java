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
     * Records the total amount refunded so far (refunds are made in the provider's dashboard).
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
        status = refundedCents == amountCents ? PaymentState.REFUNDED : PaymentState.PARTIALLY_REFUNDED;
        updatedAt = now;
        return true;
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
