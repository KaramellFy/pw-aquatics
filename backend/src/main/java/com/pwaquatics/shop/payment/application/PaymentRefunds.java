package com.pwaquatics.shop.payment.application;

import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.common.error.ConflictException;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.payment.domain.Payment;
import com.pwaquatics.shop.payment.domain.PaymentMethod;
import com.pwaquatics.shop.payment.domain.PaymentRefund;
import com.pwaquatics.shop.payment.domain.PaymentRefundState;
import com.pwaquatics.shop.payment.domain.PaymentRefunded;
import com.pwaquatics.shop.payment.infrastructure.PaymentRefundRepository;
import com.pwaquatics.shop.payment.infrastructure.PaymentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refunds the shop owner makes from the admin order page (docs/adr/010-payments.md): all or part
 * of a card or PayPal payment goes back to the customer through the provider that took it.
 * Bank transfers are paid back by the owner's own bank transfer.
 *
 * <ul>
 *   <li>The amount may not exceed what is left to refund, read from the stored payment; the
 *       request also carries the amount the owner saw as refundable, so a refund made meanwhile
 *       (another tab, the provider's dashboard) is noticed instead of guessed around.
 *   <li>Each refund has an idempotency key, passed on to the provider: a repeated request
 *       (double click, retry after a lost answer) returns the first refund instead of paying twice.
 *   <li>A refund counts as refunded as soon as the provider accepts it, as the providers count it.
 *       If it fails later, the provider's webhook takes it back and the shop inbox is told.
 * </ul>
 */
@Service
public class PaymentRefunds {

    private static final Logger log = LoggerFactory.getLogger(PaymentRefunds.class);

    /**
     * @param expectedRefundable what the owner saw as refundable when deciding
     * @param reason the owner's internal reason, or null
     * @param requestedBy the owner's user id
     */
    public record RefundRequest(
            UUID orderId,
            PaymentMethod provider,
            Money amount,
            Money expectedRefundable,
            String idempotencyKey,
            String reason,
            UUID requestedBy) {}

    /**
     * @param status where the refund stands: succeeded, or pending while the provider pays it out
     * @param repeated true if the idempotency key had been used for this refund before; nothing
     *     new happened
     */
    public record RefundResult(Money amount, PaymentRefundState status, boolean repeated) {}

    private final StripeGateway stripe;
    private final PayPalGateway paypal;
    private final PaymentRepository payments;
    private final PaymentRefundRepository refunds;
    private final PayableOrders orders;
    private final Clock clock;

    PaymentRefunds(
            StripeGateway stripe,
            PayPalGateway paypal,
            PaymentRepository payments,
            PaymentRefundRepository refunds,
            PayableOrders orders,
            Clock clock) {
        this.stripe = stripe;
        this.paypal = paypal;
        this.payments = payments;
        this.refunds = refunds;
        this.orders = orders;
        this.clock = clock;
    }

    /**
     * Refunds money of an order's card or PayPal payment through the provider. The caller holds
     * the lock on the order; the payment is locked here (order first, then payment, as everywhere).
     * The order's payment status follows through {@link PayableOrders#paymentRefunded}.
     *
     * @throws ConflictException {@code NOT_REFUNDABLE} (no online payment, not paid, or refunded
     *     in full), {@code REFUNDS_CHANGED} (the refundable amount is no longer what the owner
     *     saw), {@code IDEMPOTENCY_KEY_REUSED} (the key belongs to a different refund)
     * @throws BusinessRuleException {@code REFUND_AMOUNT_INVALID}; {@code REFUND_REFUSED} if the
     *     provider refuses (nothing is recorded then)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public RefundResult refund(RefundRequest request) {
        Payment payment = payments.lockByOrderIdAndProvider(request.orderId(), request.provider())
                .filter(found -> found.getProvider() != PaymentMethod.BANK_TRANSFER)
                .orElseThrow(() -> new ConflictException(
                        "NOT_REFUNDABLE", "Only card and PayPal payments can be refunded here."));

        Optional<PaymentRefund> earlier = refunds.findByIdempotencyKey(request.idempotencyKey());
        if (earlier.isPresent()) {
            return repeated(earlier.get(), payment, request);
        }

        Money refundable = payment.refundable();
        if (!payment.getStatus().isSettled()) {
            throw new ConflictException("NOT_REFUNDABLE", "This payment has not arrived, so there is nothing to refund.");
        }
        if (refundable.amount() == 0) {
            throw new ConflictException("NOT_REFUNDABLE", "This payment has been refunded in full.");
        }
        if (!refundable.equals(request.expectedRefundable())) {
            throw new ConflictException("REFUNDS_CHANGED",
                    "The amount left to refund has changed in the meantime. Reload the order and check it.");
        }
        Money amount = request.amount();
        if (amount.amount() <= 0 || amount.amount() > refundable.amount()
                || !amount.currency().equals(refundable.currency())) {
            throw new BusinessRuleException("REFUND_AMOUNT_INVALID",
                    "Refund more than nothing and at most what is left to refund.");
        }

        Instant now = clock.instant();
        PaymentRefund refund = refunds.save(PaymentRefund.requested(
                payment, amount, request.idempotencyKey(), request.reason(), request.requestedBy(), now));
        PaymentRefundState status = switch (payment.getProvider()) {
            case STRIPE -> refundAtStripe(payment, refund);
            case PAYPAL -> refundAtPayPal(payment, refund);
            case BANK_TRANSFER -> throw new IllegalStateException("Bank transfers are refunded by hand");
        };
        payment.refundAccepted(amount, now);
        orders.paymentRefunded(new PaymentRefunded(payment.getOrderId(), payment.refunded(), payment.isFullyRefunded()));
        return new RefundResult(amount, status, false);
    }

    /**
     * The provider reports where one of our refunds stands (Stripe's {@code refund.*} events,
     * PayPal's {@code PAYMENT.CAPTURE.REFUNDED}). Refunds made in the provider's dashboard are
     * not ours; they are counted through the payment's refunded total instead. Runs in the
     * webhook's transaction; locks order, payment and refund in that order.
     *
     * @return a note for the webhook log, or null if the refund was found
     */
    @Transactional(propagation = Propagation.MANDATORY)
    String providerReport(String providerReference, PaymentRefundState outcome, String failureCode) {
        Optional<UUID> orderId = refunds.findOrderIdByProviderReference(providerReference);
        if (orderId.isEmpty()) {
            return "refund not made from the admin";
        }
        orders.lock(orderId.get());
        PaymentRefund refund = refunds.lockByProviderReference(providerReference).orElseThrow();
        Payment payment = payments.findById(refund.getPaymentId()).orElseThrow();
        payments.lockByOrderIdAndProvider(payment.getOrderId(), payment.getProvider());
        Instant now = clock.instant();
        if (refund.finished(outcome, failureCode, now) && outcome == PaymentRefundState.FAILED) {
            log.warn("Refund {} of payment {} failed at the provider ({})", refund.getId(), payment.getId(), failureCode);
            if (payment.refundFailed(refund.amount(), now)) {
                orders.paymentRefunded(
                        new PaymentRefunded(payment.getOrderId(), payment.refunded(), payment.isFullyRefunded()));
            }
            orders.refundFailed(payment.getOrderId(), refund.amount());
        }
        return null;
    }

    private RefundResult repeated(PaymentRefund refund, Payment payment, RefundRequest request) {
        if (!refund.getPaymentId().equals(payment.getId()) || !refund.amount().equals(request.amount())) {
            throw new ConflictException("IDEMPOTENCY_KEY_REUSED", "This request key belongs to a different refund.");
        }
        return new RefundResult(refund.amount(), refund.getStatus(), true);
    }

    private PaymentRefundState refundAtStripe(Payment payment, PaymentRefund refund) {
        StripeGateway.RefundData answer;
        try {
            answer = stripe.refund(payment.getProviderReference(), refund.amount(), refund.getIdempotencyKey());
        } catch (StripeGateway.RefundRefused e) {
            throw refused("Stripe", e.code());
        }
        return accepted(refund, answer.id(), answer.amount(), stripeState(answer.status()), "Stripe", answer.status());
    }

    private PaymentRefundState refundAtPayPal(Payment payment, PaymentRefund refund) {
        if (payment.getCaptureReference() == null) {
            throw new IllegalStateException("A settled PayPal payment without a capture");
        }
        PayPalGateway.Refund answer;
        try {
            answer = paypal.refund(payment.getCaptureReference(), refund.amount(), refund.getIdempotencyKey());
        } catch (PayPalGateway.RefundRefused e) {
            throw refused("PayPal", e.issue());
        }
        return accepted(refund, answer.id(), answer.amount(), payPalState(answer.status()), "PayPal", answer.status());
    }

    /** Records the provider's answer; a refund that failed straight away is refused like any other. */
    private PaymentRefundState accepted(
            PaymentRefund refund, String reference, Money amount, PaymentRefundState status, String provider, String raw) {
        if (!refund.amount().equals(amount)) {
            throw new IllegalStateException(provider + " refunded a different amount than requested");
        }
        if (status == PaymentRefundState.FAILED) {
            throw refused(provider, raw.toLowerCase(Locale.ROOT));
        }
        refund.answered(reference, status, clock.instant());
        return status;
    }

    private static BusinessRuleException refused(String provider, String code) {
        return new BusinessRuleException("REFUND_REFUSED", "%s refused the refund (%s).".formatted(provider, code));
    }

    /** Stripe's refund status as ours; {@code requires_action} waits like {@code pending}. */
    static PaymentRefundState stripeState(String status) {
        return switch (status) {
            case "succeeded" -> PaymentRefundState.SUCCEEDED;
            case "failed", "canceled" -> PaymentRefundState.FAILED;
            default -> PaymentRefundState.PENDING;
        };
    }

    static PaymentRefundState payPalState(String status) {
        return switch (status) {
            case "COMPLETED" -> PaymentRefundState.SUCCEEDED;
            case "FAILED", "CANCELLED" -> PaymentRefundState.FAILED;
            default -> PaymentRefundState.PENDING;
        };
    }
}
