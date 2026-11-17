package com.pwaquatics.shop.payment.application;

import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.common.error.ConflictException;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.payment.application.PayPalGateway.Capture;
import com.pwaquatics.shop.payment.application.PayPalGateway.CaptureRefused;
import com.pwaquatics.shop.payment.application.PayPalGateway.PayPalOrder;
import com.pwaquatics.shop.payment.application.view.PayPalPaymentSession;
import com.pwaquatics.shop.payment.application.view.PaymentSummaryView;
import com.pwaquatics.shop.payment.domain.Payment;
import com.pwaquatics.shop.payment.domain.PaymentMethod;
import com.pwaquatics.shop.payment.domain.PaymentState;
import com.pwaquatics.shop.payment.infrastructure.PaymentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payments with PayPal (PayPal's button and the Orders API, docs/adr/010-payments.md).
 *
 * <p>Each shop order gets exactly one PayPal order, created for the stored order total. The
 * customer approves it in PayPal's window; then our server takes the money ("capture") and
 * reads the result from PayPal itself. The browser only reports that the customer approved; it
 * never decides that an order is paid. PayPal's verified webhooks ({@link PayPalWebhooks})
 * confirm the same and cover the cases where the browser never comes back.
 */
@Service
public class PayPalPayments {

    /** What capturing a payment led to. */
    public enum CaptureOutcome {
        /** PayPal has the money. */
        PAID,
        /** PayPal or the bank still checks the payment; a webhook follows. */
        PENDING,
        /** Not taken (e.g. the funding source was declined); the customer may approve again. */
        DECLINED
    }

    /**
     * @param newlyPaid true only the first time the money is known to have arrived, so the
     *     order is marked as paid exactly once
     */
    public record CaptureResult(CaptureOutcome outcome, boolean newlyPaid, Money amount) {}

    private final PayPalGateway paypal;
    private final PaymentRepository payments;
    private final AppProperties.PayPal account;
    private final RefundHistory history;
    private final Clock clock;

    PayPalPayments(
            PayPalGateway paypal,
            PaymentRepository payments,
            RefundHistory history,
            AppProperties properties,
            Clock clock) {
        this.paypal = paypal;
        this.payments = payments;
        this.history = history;
        this.account = properties.paypal();
        this.clock = clock;
    }

    /** Offered only when the PayPal account is configured. */
    public boolean isAvailable() {
        return account.isConfigured();
    }

    /** The order's PayPal payment, if one was started; for the admin order view. */
    @Transactional(readOnly = true)
    public Optional<PaymentSummaryView> summary(UUID orderId) {
        String site = account.isSandbox() ? "https://www.sandbox.paypal.com" : "https://www.paypal.com";
        return payments.findByOrderIdAndProvider(orderId, PaymentMethod.PAYPAL).map(payment -> new PaymentSummaryView(
                payment.getProvider(),
                payment.getCaptureReference() != null ? payment.getCaptureReference() : payment.getProviderReference(),
                payment.getStatus(),
                payment.amount(),
                payment.refunded(),
                payment.refundable(),
                history.of(payment),
                payment.getMethodDetail(),
                payment.getFailureCode(),
                payment.getCaptureReference() != null
                        ? site + "/activity/payment/" + payment.getCaptureReference()
                        : site + "/myaccount/transactions/"));
    }

    /**
     * The PayPal order for a shop order: created on first use, afterwards the existing one. The
     * caller must hold the lock on the order and have checked that it is waiting for this payment.
     *
     * @param amount the stored order total
     * @param language "de" or "en", for PayPal's pages
     * @throws ConflictException {@code ORDER_NOT_PAYABLE} if PayPal has voided the PayPal order
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public PayPalPaymentSession prepare(UUID orderId, String orderNumber, Money amount, String language) {
        if (!isAvailable()) {
            throw new IllegalStateException("PayPal is not configured");
        }
        Optional<Payment> existing = payments.findByOrderIdAndProvider(orderId, PaymentMethod.PAYPAL);
        PayPalOrder order;
        if (existing.isPresent()) {
            if (existing.get().getStatus().isSettled() || existing.get().getStatus() == PaymentState.PENDING) {
                return new PayPalPaymentSession(existing.get().getProviderReference(), account.clientId(), true);
            }
            order = paypal.getOrder(existing.get().getProviderReference());
        } else {
            order = paypal.createOrder(orderId, orderNumber, amount, language);
            payments.save(Payment.paypal(orderId, order.id(), amount, clock.instant()));
        }
        if (!order.amount().equals(amount)) {
            throw new IllegalStateException("The PayPal order amount differs from the order total");
        }
        if (order.isVoided()) {
            throw new ConflictException("ORDER_NOT_PAYABLE", "This order can no longer be paid.");
        }
        Capture capture = order.capture();
        boolean paid = capture != null && (capture.isCompleted() || capture.isPending());
        return new PayPalPaymentSession(order.id(), account.clientId(), paid);
    }

    /**
     * Takes the money the customer approved in PayPal's window. The caller must hold the lock
     * on the order and have checked that it is waiting for this payment.
     *
     * @throws ConflictException {@code PAYMENT_NOT_STARTED} if the order has no PayPal payment yet
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CaptureResult capture(UUID orderId) {
        Payment payment = payments.lockByOrderIdAndProvider(orderId, PaymentMethod.PAYPAL)
                .orElseThrow(() -> new ConflictException(
                        "PAYMENT_NOT_STARTED", "Start the PayPal payment of this order first."));
        return capture(payment);
    }

    /**
     * Captures a locked payment (also used when PayPal reports an approval by webhook). A payment
     * that already has its money is not captured again.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    CaptureResult capture(Payment payment) {
        Instant now = clock.instant();
        if (payment.getStatus().isSettled()) {
            return new CaptureResult(CaptureOutcome.PAID, false, payment.amount());
        }
        if (payment.getStatus() == PaymentState.PENDING) {
            return new CaptureResult(CaptureOutcome.PENDING, false, payment.amount());
        }
        if (payment.getStatus() == PaymentState.CANCELLED) {
            throw new ConflictException("ORDER_NOT_PAYABLE", "This order can no longer be paid.");
        }
        PayPalOrder order;
        try {
            order = paypal.capture(payment.getProviderReference(), payment.getOrderId());
        } catch (CaptureRefused e) {
            payment.failed(e.issue(), now);
            return new CaptureResult(CaptureOutcome.DECLINED, false, payment.amount());
        }
        if (order.capture() == null) {
            throw new IllegalStateException("PayPal reported a capture without its details");
        }
        return apply(payment, order.capture(), now);
    }

    /**
     * Applies what PayPal reports about a capture to our payment.
     *
     * @throws IllegalArgumentException if PayPal reports a different amount (nothing is marked as paid)
     */
    CaptureResult apply(Payment payment, Capture capture, Instant now) {
        payment.capturedAs(capture.id(), now);
        if (capture.isCompleted()) {
            boolean first = payment.succeeded(capture.amount(), "paypal", now);
            return new CaptureResult(CaptureOutcome.PAID, first, payment.amount());
        }
        if (capture.isPending()) {
            payment.processing(now);
            return new CaptureResult(CaptureOutcome.PENDING, false, payment.amount());
        }
        payment.failed(capture.status(), now);
        return new CaptureResult(CaptureOutcome.DECLINED, false, payment.amount());
    }

    /**
     * Before an unpaid order is cancelled. PayPal orders cannot be withdrawn, but we never take
     * the money for a cancelled order, so marking our payment as cancelled is enough — unless
     * PayPal already has the money or is processing it.
     *
     * @return true if the order may be cancelled; false if PayPal has the money or is processing
     *     it (its webhook will settle the order)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean cancelOpenPayment(UUID orderId) {
        Optional<Payment> found = payments.lockByOrderIdAndProvider(orderId, PaymentMethod.PAYPAL);
        if (found.isEmpty()) {
            return true;
        }
        Payment payment = found.get();
        if (payment.getStatus().isSettled() || payment.getStatus() == PaymentState.PENDING) {
            return false;
        }
        if (payment.getStatus() == PaymentState.CANCELLED) {
            return true;
        }
        // A capture whose answer never reached us (e.g. a timeout) shows up here.
        Capture capture = paypal.getOrder(payment.getProviderReference()).capture();
        if (capture != null && (capture.isCompleted() || capture.isPending())) {
            return false;
        }
        payment.cancelled(clock.instant());
        return true;
    }
}
