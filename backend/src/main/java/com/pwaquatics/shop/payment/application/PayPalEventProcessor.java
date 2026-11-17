package com.pwaquatics.shop.payment.application;

import com.pwaquatics.shop.payment.application.PayPalGateway.Capture;
import com.pwaquatics.shop.payment.application.PayPalPayments.CaptureOutcome;
import com.pwaquatics.shop.payment.application.PayPalPayments.CaptureResult;
import com.pwaquatics.shop.payment.domain.Payment;
import com.pwaquatics.shop.payment.domain.PaymentMethod;
import com.pwaquatics.shop.payment.domain.PaymentRefunded;
import com.pwaquatics.shop.payment.domain.PaymentSucceeded;
import com.pwaquatics.shop.payment.infrastructure.PaymentRepository;
import com.pwaquatics.shop.payment.infrastructure.WebhookEventLog;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Applies one verified PayPal event to our payment record, in one transaction together with its
 * effect on the order ({@link PayableOrders}). Events we do not use are recorded and ignored.
 */
@Component
class PayPalEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(PayPalEventProcessor.class);
    private static final String CAPTURE_LINK = "/v2/payments/captures/";

    private final WebhookEventLog events;
    private final PaymentRepository payments;
    private final PayPalPayments paypal;
    private final PayableOrders orders;
    private final PaymentRefunds refunds;
    private final Clock clock;

    PayPalEventProcessor(
            WebhookEventLog events,
            PaymentRepository payments,
            PayPalPayments paypal,
            PayableOrders orders,
            PaymentRefunds refunds,
            Clock clock) {
        this.events = events;
        this.payments = payments;
        this.paypal = paypal;
        this.orders = orders;
        this.refunds = refunds;
        this.clock = clock;
    }

    @Transactional
    void process(UUID row, String type, JsonNode resource) {
        if (events.lockAndGetProcessedAt(row).isPresent()) {
            return; // A repeated delivery: already done.
        }
        Instant now = clock.instant();
        String note = switch (type) {
            case "CHECKOUT.ORDER.APPROVED" -> approved(resource);
            case "PAYMENT.CAPTURE.COMPLETED", "PAYMENT.CAPTURE.PENDING", "PAYMENT.CAPTURE.DENIED",
                    "PAYMENT.CAPTURE.DECLINED" -> captured(resource, now);
            case "PAYMENT.CAPTURE.REFUNDED", "PAYMENT.CAPTURE.REVERSED" -> refunded(resource, now);
            default -> "ignored";
        };
        events.markProcessed(row, now, note);
    }

    /**
     * The customer approved the payment, but the shop may not have heard it from the browser
     * (closed tab, lost connection): take the money now, while the order still waits for it.
     */
    private String approved(JsonNode order) {
        Optional<Payment> found = byPayPalOrder(order.path("id").asString(""));
        if (found.isEmpty()) {
            return "unknown payment";
        }
        Payment payment = found.get();
        if (!orders.isAwaitingPayment(payment.getOrderId())) {
            return "order not awaiting payment";
        }
        try {
            settle(payment, paypal.capture(payment));
            return null;
        } catch (IllegalArgumentException e) {
            return mismatch(payment);
        }
    }

    private String captured(JsonNode capture, Instant now) {
        String paypalOrderId = capture.path("supplementary_data").path("related_ids").path("order_id").asString("");
        Optional<Payment> found = byPayPalOrder(paypalOrderId);
        if (found.isEmpty()) {
            return "unknown payment";
        }
        Payment payment = found.get();
        try {
            Capture reported = new Capture(
                    capture.path("id").asString(""),
                    capture.path("status").asString(""),
                    PayPalAmounts.money(
                            capture.path("amount").path("value").asString(null),
                            capture.path("amount").path("currency_code").asString(null)));
            settle(payment, paypal.apply(payment, reported, now));
            return null;
        } catch (IllegalArgumentException e) {
            return mismatch(payment);
        }
    }

    /**
     * A refund (from the admin order page or PayPal's site) or a reversal: the resource names the
     * capture it belongs to. A refund made from the admin is marked as completed as well.
     */
    private String refunded(JsonNode refund, Instant now) {
        String captureId = captureIdOf(refund);
        if (captureId == null) {
            return "unknown payment";
        }
        Optional<UUID> orderId = payments.findOrderIdByCaptureReference(captureId);
        if (orderId.isEmpty()) {
            return "unknown payment";
        }
        orders.lock(orderId.get());
        Payment payment = payments.lockByCaptureReference(captureId).orElseThrow();
        JsonNode total = refund.path("seller_payable_breakdown").path("total_refunded_amount");
        if (!total.isObject()) {
            total = refund.path("amount");
        }
        long refundedCents = PayPalAmounts.money(
                total.path("value").asString(null), total.path("currency_code").asString(null)).amount();
        if (payment.refunded(refundedCents, now)) {
            orders.paymentRefunded(
                    new PaymentRefunded(payment.getOrderId(), payment.refunded(), payment.isFullyRefunded()));
        }
        String refundId = refund.path("id").asString("");
        if (refundId.matches("[A-Za-z0-9]{1,100}")) {
            refunds.providerReport(refundId, PaymentRefunds.payPalState(refund.path("status").asString("")), null);
        }
        return null;
    }

    private void settle(Payment payment, CaptureResult result) {
        if (result.outcome() == CaptureOutcome.PAID && result.newlyPaid()) {
            orders.paymentSucceeded(new PaymentSucceeded(payment.getOrderId(), PaymentMethod.PAYPAL, payment.amount()));
        }
    }

    private static String mismatch(Payment payment) {
        // Never mark an order as paid for a different amount or capture; the owner has to look at it.
        log.warn("PayPal reported a capture that does not match payment {}; the order was not marked as paid",
                payment.getId());
        return "amount mismatch";
    }

    /**
     * Our payment for a PayPal order id, locked together with its order (order first, as
     * everywhere else). PayPal orders of other systems on the same account find nothing.
     */
    private Optional<Payment> byPayPalOrder(String paypalOrderId) {
        if (paypalOrderId.isBlank()) {
            return Optional.empty();
        }
        Optional<UUID> orderId = payments.findOrderIdByProviderReference(paypalOrderId);
        if (orderId.isEmpty()) {
            return Optional.empty();
        }
        orders.lock(orderId.get());
        return payments.lockByProviderReference(paypalOrderId)
                .filter(payment -> payment.getProvider() == PaymentMethod.PAYPAL);
    }

    /** The capture a refund belongs to, from its "up" link (…/v2/payments/captures/{id}). */
    static String captureIdOf(JsonNode refund) {
        for (JsonNode link : refund.path("links")) {
            String href = link.path("href").asString("");
            int at = href.indexOf(CAPTURE_LINK);
            if ("up".equals(link.path("rel").asString("")) && at >= 0) {
                String id = href.substring(at + CAPTURE_LINK.length());
                return id.matches("[A-Za-z0-9]{1,100}") ? id : null;
            }
        }
        return null;
    }
}
