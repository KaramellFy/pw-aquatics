package com.pwaquatics.shop.payment.application;

import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.common.error.ConflictException;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.payment.application.StripeGateway.PaymentIntentData;
import com.pwaquatics.shop.payment.application.view.CardPaymentSession;
import com.pwaquatics.shop.payment.domain.Payment;
import com.pwaquatics.shop.payment.domain.PaymentMethod;
import com.pwaquatics.shop.payment.infrastructure.PaymentRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Card payments (including Apple Pay and Google Pay) through Stripe's Payment Element.
 *
 * <p>Each order gets exactly one PaymentIntent, created for the stored order total; a second
 * attempt after a declined card reuses it. Whether an order is paid is decided only by
 * Stripe's signed webhook ({@link StripeWebhooks}), never by the browser.
 */
@Service
public class StripePayments {

    private final StripeGateway stripe;
    private final PaymentRepository payments;
    private final AppProperties.Stripe account;
    private final Clock clock;

    StripePayments(StripeGateway stripe, PaymentRepository payments, AppProperties properties, Clock clock) {
        this.stripe = stripe;
        this.payments = payments;
        this.account = properties.stripe();
        this.clock = clock;
    }

    /** Offered only when the Stripe account is configured. */
    public boolean isAvailable() {
        return account.isConfigured();
    }

    /**
     * The payment form for an order: creates the PaymentIntent on first use, afterwards
     * returns the existing one. The caller must hold the lock on the order and have checked
     * that it is waiting for this payment.
     *
     * @param amount the stored order total
     * @throws ConflictException {@code ORDER_NOT_PAYABLE} if the payment was cancelled at Stripe
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CardPaymentSession prepare(UUID orderId, String orderNumber, Money amount) {
        if (!isAvailable()) {
            throw new IllegalStateException("Stripe is not configured");
        }
        Optional<Payment> existing = payments.findByOrderIdAndProvider(orderId, PaymentMethod.STRIPE);
        PaymentIntentData intent;
        if (existing.isPresent()) {
            intent = stripe.retrievePaymentIntent(existing.get().getProviderReference());
        } else {
            intent = stripe.createPaymentIntent(orderId, orderNumber, amount);
            payments.save(Payment.stripe(orderId, intent.id(), amount, clock.instant()));
        }
        if (!intent.amount().equals(amount)) {
            throw new IllegalStateException("The PaymentIntent amount differs from the order total");
        }
        if (intent.isCanceled()) {
            throw new ConflictException("ORDER_NOT_PAYABLE", "This order can no longer be paid.");
        }
        return new CardPaymentSession(intent.clientSecret(), account.publishableKey(), intent.isSucceeded());
    }

    /**
     * Before an unpaid order is cancelled: cancels its PaymentIntent at Stripe, so the customer
     * cannot pay for an order that no longer exists.
     *
     * @return true if the order may be cancelled (no payment was started, or it is cancelled
     *     now); false if Stripe already has the money or is processing it — the webhook will
     *     mark the order as paid
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean cancelOpenPayment(UUID orderId) {
        Optional<Payment> found = payments.lockByOrderIdAndProvider(orderId, PaymentMethod.STRIPE);
        if (found.isEmpty()) {
            return true;
        }
        Payment payment = found.get();
        if (payment.getStatus().isSettled()) {
            return false;
        }
        PaymentIntentData intent = stripe.cancelPaymentIntent(payment.getProviderReference());
        if (!intent.isCanceled()) {
            return false;
        }
        payment.cancelled(clock.instant());
        return true;
    }
}
