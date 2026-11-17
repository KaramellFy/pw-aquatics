package com.pwaquatics.shop.payment.application.view;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.payment.domain.PaymentRefundState;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.UUID;

/**
 * A refund the shop owner made from the admin order page. Refunds made in the provider's
 * dashboard are not listed; they only show in the payment's refunded total.
 *
 * @param reference the provider's id of the refund, e.g. Stripe's {@code re_…}
 * @param reason the owner's internal reason
 * @param failureCode the provider's reason for a failed refund, e.g. {@code expired_or_canceled_card}
 * @param requestedBy the name of the person who made the refund
 */
public record PaymentRefundView(
        UUID id,
        Money amount,
        PaymentRefundState status,
        Instant createdAt,
        @Nullable String reference,
        @Nullable String reason,
        @Nullable String failureCode,
        @Nullable String requestedBy) {}
