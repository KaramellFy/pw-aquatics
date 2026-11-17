package com.pwaquatics.shop.support;

import com.pwaquatics.shop.common.error.ServiceUnavailableException;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.payment.application.PayPalGateway;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Primary;

/**
 * Stands in for PayPal's API in integration tests, so they never reach the network. Behaves like
 * PayPal where it matters: one PayPal order per shop order ({@code PayPal-Request-Id}), the money
 * can only be taken after the customer approved, and a second capture returns the first one.
 * A webhook delivery counts as genuine when it carries {@link #GENUINE_SIGNATURE}.
 */
@Primary
public class FakePayPalGateway implements PayPalGateway {

    /** The {@code PAYPAL-TRANSMISSION-SIG} that this fake accepts as PayPal's. */
    public static final String GENUINE_SIGNATURE = "genuine-test-signature";

    private final Map<UUID, String> orderByShopOrder = new ConcurrentHashMap<>();
    private final Map<String, PayPalOrder> orders = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();
    private final AtomicInteger captureCalls = new AtomicInteger();
    private volatile String nextRefusal;
    private volatile String captureStatus = "COMPLETED";
    private final Map<String, Refund> refundsByKey = new ConcurrentHashMap<>();
    private final List<RefundCall> refundCalls = new CopyOnWriteArrayList<>();
    private volatile String nextRefundRefusal;
    private volatile String refundStatus = "COMPLETED";
    private volatile boolean down;

    /** A refund the shop asked PayPal for. */
    public record RefundCall(String captureId, Money amount, String idempotencyKey) {}

    @Override
    public PayPalOrder createOrder(UUID orderId, String orderNumber, Money amount, String language) {
        checkUp();
        String id = orderByShopOrder.computeIfAbsent(orderId, ignored -> {
            String newId = "TESTPAYPAL%07d".formatted(sequence.incrementAndGet());
            orders.put(newId, new PayPalOrder(newId, "CREATED", amount, null));
            return newId;
        });
        return orders.get(id);
    }

    @Override
    public PayPalOrder getOrder(String paypalOrderId) {
        checkUp();
        return require(paypalOrderId);
    }

    @Override
    public PayPalOrder capture(String paypalOrderId, UUID orderId) {
        checkUp();
        captureCalls.incrementAndGet();
        PayPalOrder order = require(paypalOrderId);
        if (order.capture() != null) {
            return order;
        }
        String refusal = nextRefusal;
        if (refusal != null) {
            nextRefusal = null;
            throw new CaptureRefused(refusal);
        }
        if (!"APPROVED".equals(order.status())) {
            throw new CaptureRefused("ORDER_NOT_APPROVED");
        }
        return takeMoney(paypalOrderId, captureStatus);
    }

    @Override
    public boolean isGenuine(WebhookSignature signature, String payload) {
        checkUp();
        return GENUINE_SIGNATURE.equals(signature.transmissionSig());
    }

    /** Like PayPal: the same {@code PayPal-Request-Id} returns the first refund. */
    @Override
    public Refund refund(String captureId, Money amount, String idempotencyKey) {
        checkUp();
        refundCalls.add(new RefundCall(captureId, amount, idempotencyKey));
        Refund earlier = refundsByKey.get(idempotencyKey);
        if (earlier != null) {
            return earlier;
        }
        String refusal = nextRefundRefusal;
        if (refusal != null) {
            nextRefundRefusal = null;
            throw new RefundRefused(refusal);
        }
        Refund refund = new Refund("TESTREFUND%06d".formatted(sequence.incrementAndGet()), refundStatus, amount);
        refundsByKey.put(idempotencyKey, refund);
        return refund;
    }

    // ------------------------------------------------------------------ test controls

    /** The next refund is refused, e.g. with {@code REFUND_TIME_LIMIT_EXCEEDED}. */
    public void refuseNextRefund(String issue) {
        nextRefundRefusal = issue;
    }

    /** What the next refunds report: {@code COMPLETED} (default) or {@code PENDING}. */
    public void setRefundStatus(String status) {
        refundStatus = status;
    }

    public List<RefundCall> refundCalls() {
        return List.copyOf(refundCalls);
    }

    /** The customer approved the payment in PayPal's window. */
    public void approve(String paypalOrderId) {
        PayPalOrder order = require(paypalOrderId);
        orders.put(paypalOrderId, new PayPalOrder(paypalOrderId, "APPROVED", order.amount(), null));
    }

    /**
     * PayPal took the money, but its answer never reached the shop (e.g. a timeout).
     *
     * @return the capture id
     */
    public String takeMoneyBehindTheShopsBack(String paypalOrderId, String status) {
        return takeMoney(paypalOrderId, status).capture().id();
    }

    /** The next capture is refused, e.g. with {@code INSTRUMENT_DECLINED}. */
    public void refuseNextCapture(String issue) {
        nextRefusal = issue;
    }

    /** What the next captures report: {@code COMPLETED} (default) or {@code PENDING}. */
    public void setCaptureStatus(String status) {
        captureStatus = status;
    }

    public void setDown(boolean down) {
        this.down = down;
    }

    /** The PayPal order created for a shop order, or null. */
    public String orderOf(UUID shopOrderId) {
        return orderByShopOrder.get(shopOrderId);
    }

    public int captureCalls() {
        return captureCalls.get();
    }

    /** Back to normal after a test that changed the behaviour. */
    public void reset() {
        nextRefusal = null;
        captureStatus = "COMPLETED";
        nextRefundRefusal = null;
        refundStatus = "COMPLETED";
        down = false;
    }

    private PayPalOrder takeMoney(String paypalOrderId, String status) {
        PayPalOrder order = require(paypalOrderId);
        Capture capture = new Capture("TESTCAPTURE%06d".formatted(sequence.incrementAndGet()), status, order.amount());
        PayPalOrder captured = new PayPalOrder(paypalOrderId, "COMPLETED", order.amount(), capture);
        orders.put(paypalOrderId, captured);
        return captured;
    }

    private PayPalOrder require(String paypalOrderId) {
        PayPalOrder order = orders.get(paypalOrderId);
        if (order == null) {
            throw new IllegalArgumentException("Unknown PayPal order " + paypalOrderId);
        }
        return order;
    }

    private void checkUp() {
        if (down) {
            throw new ServiceUnavailableException(
                    "PAYMENT_PROVIDER_UNAVAILABLE", "The payment service is not available right now. Please try again.",
                    null);
        }
    }
}
