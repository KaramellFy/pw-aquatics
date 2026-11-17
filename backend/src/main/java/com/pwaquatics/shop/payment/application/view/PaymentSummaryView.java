package com.pwaquatics.shop.payment.application.view;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.payment.domain.PaymentMethod;
import com.pwaquatics.shop.payment.domain.PaymentState;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * An online payment of an order as the shop owner sees it (no card details exist here).
 *
 * @param reference the provider's id, e.g. Stripe's PaymentIntent {@code pi_…}
 * @param refunded everything refunded so far, from the admin and the provider's dashboard
 *     (refunds the provider is still paying out included)
 * @param refundable what may still be refunded (zero until the money has arrived)
 * @param refunds the refunds made from the admin order page, oldest first
 * @param failureCode the last decline reason, e.g. {@code card_declined}
 * @param dashboardUrl the payment in the provider's dashboard (for disputes)
 */
public record PaymentSummaryView(
        PaymentMethod provider,
        String reference,
        PaymentState status,
        Money amount,
        Money refunded,
        Money refundable,
        List<PaymentRefundView> refunds,
        @Nullable String methodDetail,
        @Nullable String failureCode,
        String dashboardUrl) {}
