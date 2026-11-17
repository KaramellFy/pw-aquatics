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
 * A refund the shop owner started from the admin order page. Refunds made in the provider's
 * dashboard have no row here; they only show in {@link Payment#refunded()}.
 */
@Entity
@Table(name = "payment_refunds")
public class PaymentRefund extends AbstractEntity {

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "amount_cents", nullable = false, updatable = false)
    private long amountCents;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentRefundState status;

    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(length = 500, updatable = false)
    private String reason;

    @Column(name = "requested_by", updatable = false)
    private UUID requestedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected PaymentRefund() {}

    /** A refund about to be sent to the provider. */
    public static PaymentRefund requested(
            Payment payment, Money amount, String idempotencyKey, String reason, UUID requestedBy, Instant now) {
        if (amount.amount() <= 0 || !amount.currency().equals(payment.amount().currency())) {
            throw new IllegalArgumentException("A refund needs a positive amount in the payment's currency");
        }
        PaymentRefund refund = new PaymentRefund();
        refund.paymentId = payment.getId();
        refund.idempotencyKey = idempotencyKey;
        refund.amountCents = amount.amount();
        refund.currency = amount.currency();
        refund.status = PaymentRefundState.PENDING;
        refund.reason = reason;
        refund.requestedBy = requestedBy;
        refund.createdAt = now;
        refund.updatedAt = now;
        return refund;
    }

    /** The provider answered; {@code status} is where the refund stands now. */
    public void answered(String providerReference, PaymentRefundState status, Instant now) {
        this.providerReference = providerReference;
        this.status = status;
        this.updatedAt = now;
    }

    /**
     * The provider reports where the refund stands now. A pending refund can succeed or fail; a
     * refund that had succeeded can still fail afterwards (Stripe: e.g. a closed card account).
     * A failed refund stays failed.
     *
     * @return true if this changed the refund
     */
    public boolean finished(PaymentRefundState outcome, String failureCode, Instant now) {
        boolean changes = switch (status) {
            case PENDING -> outcome != PaymentRefundState.PENDING;
            case SUCCEEDED -> outcome == PaymentRefundState.FAILED;
            case FAILED -> false;
        };
        if (!changes) {
            return false;
        }
        status = outcome;
        this.failureCode = failureCode;
        updatedAt = now;
        return true;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public Money amount() {
        return new Money(amountCents, currency);
    }

    public PaymentRefundState getStatus() {
        return status;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getReason() {
        return reason;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
