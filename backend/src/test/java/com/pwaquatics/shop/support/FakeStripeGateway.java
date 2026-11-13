package com.pwaquatics.shop.support;

import com.pwaquatics.shop.common.error.ServiceUnavailableException;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.payment.application.StripeGateway;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Primary;

/**
 * Stands in for Stripe's API in integration tests, so they never reach the network. Behaves
 * like Stripe where it matters: one PaymentIntent per order (idempotency key), and a
 * PaymentIntent that succeeded or is processing cannot be cancelled. The webhook signature
 * check is not faked; tests sign their events with the test secret.
 */
@Primary
public class FakeStripeGateway implements StripeGateway {

    private static final Set<String> NOT_CANCELLABLE = Set.of("succeeded", "processing", "canceled");

    private final Map<UUID, String> intentByOrder = new ConcurrentHashMap<>();
    private final Map<String, PaymentIntentData> intents = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();
    private final AtomicInteger createCalls = new AtomicInteger();
    private volatile boolean down;

    @Override
    public PaymentIntentData createPaymentIntent(UUID orderId, String orderNumber, Money amount) {
        checkUp();
        createCalls.incrementAndGet();
        String id = intentByOrder.computeIfAbsent(orderId, ignored -> {
            String newId = "pi_test_%06d".formatted(sequence.incrementAndGet());
            intents.put(newId, new PaymentIntentData(newId, "requires_payment_method", newId + "_secret_test", amount));
            return newId;
        });
        return intents.get(id);
    }

    @Override
    public PaymentIntentData retrievePaymentIntent(String paymentIntentId) {
        checkUp();
        return require(paymentIntentId);
    }

    @Override
    public PaymentIntentData cancelPaymentIntent(String paymentIntentId) {
        checkUp();
        PaymentIntentData intent = require(paymentIntentId);
        if (NOT_CANCELLABLE.contains(intent.status())) {
            return intent;
        }
        return setStatus(paymentIntentId, "canceled");
    }

    // ------------------------------------------------------------------ test controls

    /** What the customer's browser and Stripe did, e.g. {@code succeeded} or {@code processing}. */
    public PaymentIntentData setStatus(String paymentIntentId, String status) {
        PaymentIntentData intent = require(paymentIntentId);
        PaymentIntentData changed = new PaymentIntentData(intent.id(), status, intent.clientSecret(), intent.amount());
        intents.put(paymentIntentId, changed);
        return changed;
    }

    public PaymentIntentData intentOf(UUID orderId) {
        String id = intentByOrder.get(orderId);
        return id == null ? null : intents.get(id);
    }

    public int createCalls() {
        return createCalls.get();
    }

    /** Simulates Stripe being unreachable. */
    public void setDown(boolean down) {
        this.down = down;
    }

    private void checkUp() {
        if (down) {
            throw new ServiceUnavailableException(
                    "PAYMENT_PROVIDER_UNAVAILABLE", "Card payment is not available right now.", null);
        }
    }

    private PaymentIntentData require(String paymentIntentId) {
        PaymentIntentData intent = intents.get(paymentIntentId);
        if (intent == null) {
            throw new IllegalArgumentException("Unknown PaymentIntent " + paymentIntentId);
        }
        return intent;
    }
}
