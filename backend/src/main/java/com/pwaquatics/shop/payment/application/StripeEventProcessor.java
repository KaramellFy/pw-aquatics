package com.pwaquatics.shop.payment.application;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.payment.domain.Payment;
import com.pwaquatics.shop.payment.domain.PaymentMethod;
import com.pwaquatics.shop.payment.domain.PaymentRefunded;
import com.pwaquatics.shop.payment.domain.PaymentSucceeded;
import com.pwaquatics.shop.payment.infrastructure.PaymentRepository;
import com.pwaquatics.shop.payment.infrastructure.WebhookEventLog;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Applies one verified Stripe event to our payment record, in one transaction together with
 * its effect on the order ({@link PayableOrders}). Events we do not use are recorded and ignored.
 */
@Component
class StripeEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(StripeEventProcessor.class);

    private final WebhookEventLog events;
    private final PaymentRepository payments;
    private final PayableOrders orders;
    private final Clock clock;

    StripeEventProcessor(WebhookEventLog events, PaymentRepository payments, PayableOrders orders, Clock clock) {
        this.events = events;
        this.payments = payments;
        this.orders = orders;
        this.clock = clock;
    }

    @Transactional
    void process(UUID row, String type, JsonNode object) {
        if (events.lockAndGetProcessedAt(row).isPresent()) {
            return; // A repeated delivery: already done.
        }
        Instant now = clock.instant();
        String note = switch (type) {
            case "payment_intent.succeeded" -> succeeded(object, now);
            case "payment_intent.processing" -> withPayment(object.path("id"), payment -> payment.processing(now));
            case "payment_intent.payment_failed" ->
                    withPayment(object.path("id"), payment -> payment.failed(failureCode(object), now));
            case "payment_intent.canceled" -> withPayment(object.path("id"), payment -> payment.cancelled(now));
            case "charge.refunded" -> refunded(object, now);
            default -> "ignored";
        };
        events.markProcessed(row, now, note);
    }

    private String succeeded(JsonNode intent, Instant now) {
        Optional<Payment> found = payment(intent.path("id"));
        if (found.isEmpty()) {
            return "unknown payment";
        }
        Payment payment = found.get();
        String method = intent.path("payment_method_types").path(0).asString(null);
        try {
            Money received = new Money(
                    intent.path("amount_received").asLong(),
                    intent.path("currency").asString("").toUpperCase(Locale.ROOT));
            if (payment.succeeded(received, method, now)) {
                orders.paymentSucceeded(new PaymentSucceeded(payment.getOrderId(), PaymentMethod.STRIPE, payment.amount()));
            }
            return null;
        } catch (IllegalArgumentException e) {
            // Never mark an order as paid for a different amount; the owner has to look at it.
            log.warn("Stripe reported a different amount for payment {}; the order was not marked as paid",
                    payment.getId());
            return "amount mismatch";
        }
    }

    private String refunded(JsonNode charge, Instant now) {
        Optional<Payment> found = payment(charge.path("payment_intent"));
        if (found.isEmpty()) {
            return "unknown payment";
        }
        Payment payment = found.get();
        if (payment.refunded(charge.path("amount_refunded").asLong(), now)) {
            orders.paymentRefunded(
                    new PaymentRefunded(payment.getOrderId(), payment.refunded(), payment.isFullyRefunded()));
        }
        return null;
    }

    private String withPayment(JsonNode intentId, Consumer<Payment> change) {
        Optional<Payment> payment = payment(intentId);
        if (payment.isEmpty()) {
            return "unknown payment";
        }
        change.accept(payment.get());
        return null;
    }

    /**
     * Our payment for a PaymentIntent id, locked together with its order (order first, as
     * everywhere else). PaymentIntents of other systems on the same Stripe account find nothing.
     */
    private Optional<Payment> payment(JsonNode intentId) {
        String id = intentId.asString("");
        if (!id.startsWith("pi_")) {
            return Optional.empty();
        }
        Optional<UUID> orderId = payments.findOrderIdByProviderReference(id);
        if (orderId.isEmpty()) {
            return Optional.empty();
        }
        orders.lock(orderId.get());
        return payments.lockByProviderReference(id);
    }

    /** Stripe's reason for a declined attempt, e.g. {@code card_declined} or {@code insufficient_funds}. */
    static String failureCode(JsonNode intent) {
        JsonNode error = intent.path("last_payment_error");
        String code = error.path("decline_code").asString(null);
        if (code == null) {
            code = error.path("code").asString(null);
        }
        return code == null || code.length() > 100 ? null : code;
    }
}
