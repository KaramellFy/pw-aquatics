package com.pwaquatics.shop.order.application;

import com.pwaquatics.shop.audit.application.AuditService;
import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.common.error.ConflictException;
import com.pwaquatics.shop.common.error.NotFoundException;
import com.pwaquatics.shop.common.error.RateLimitedException;
import com.pwaquatics.shop.notification.application.OutboxService;
import com.pwaquatics.shop.order.application.view.CardPaymentView;
import com.pwaquatics.shop.order.domain.Order;
import com.pwaquatics.shop.order.domain.OrderStatus;
import com.pwaquatics.shop.order.domain.PaymentStatus;
import com.pwaquatics.shop.order.infrastructure.OrderRepository;
import com.pwaquatics.shop.payment.application.PayableOrders;
import com.pwaquatics.shop.payment.application.StripePayments;
import com.pwaquatics.shop.payment.application.view.CardPaymentSession;
import com.pwaquatics.shop.payment.domain.PaymentMethod;
import com.pwaquatics.shop.payment.domain.PaymentRefunded;
import com.pwaquatics.shop.payment.domain.PaymentSucceeded;
import com.pwaquatics.shop.security.ratelimit.RateLimiter;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Online payment of orders: starting a card payment for the customer, and what happens to the
 * order when Stripe confirms or refunds a payment.
 *
 * <p>The amount always comes from the stored order. The order is marked as paid only when
 * Stripe's signed webhook says so, never because the browser reports success.
 */
@Service
public class OrderPayments implements PayableOrders {

    private static final Logger log = LoggerFactory.getLogger(OrderPayments.class);

    private final OrderRepository orders;
    private final OrderViews views;
    private final StripePayments stripe;
    private final OrderEmails emails;
    private final OutboxService outbox;
    private final AuditService audit;
    private final RateLimiter rateLimiter;
    private final String shopInbox;
    private final Clock clock;

    OrderPayments(
            OrderRepository orders,
            OrderViews views,
            StripePayments stripe,
            OrderEmails emails,
            OutboxService outbox,
            AuditService audit,
            RateLimiter rateLimiter,
            AppProperties properties,
            Clock clock) {
        this.orders = orders;
        this.views = views;
        this.stripe = stripe;
        this.emails = emails;
        this.outbox = outbox;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.shopInbox = properties.mail().shopInbox();
        this.clock = clock;
    }

    /**
     * The card payment form for one of the customer's own orders. The first call creates the
     * Stripe PaymentIntent for the order total; later calls (another attempt after a declined
     * card, a reloaded page) return the same one.
     *
     * @throws NotFoundException the order is not one of the customer's own
     * @throws ConflictException {@code ORDER_ALREADY_PAID}; {@code ORDER_NOT_PAYABLE} once it is
     *     cancelled or past its payment deadline; {@code PAYMENT_METHOD_UNAVAILABLE} if it was not
     *     placed for card payment or card payment is switched off
     * @throws RateLimitedException too many attempts in a short time
     */
    @Transactional
    public CardPaymentView startCardPayment(UUID userId, String orderNumber) {
        rateLimiter.consume("card-payment", userId.toString());
        Order order = orders.lockByUserIdAndOrderNumber(userId, orderNumber)
                .orElseThrow(() -> new NotFoundException("Order not found."));
        if (order.getPaymentStatus() != PaymentStatus.UNPAID) {
            throw new ConflictException("ORDER_ALREADY_PAID", "This order has already been paid.");
        }
        if (order.getPaymentMethod() != PaymentMethod.STRIPE || !stripe.isAvailable()) {
            throw new ConflictException(
                    "PAYMENT_METHOD_UNAVAILABLE", "Card payment is not available for this order.");
        }
        if (!views.isPayableByCard(order)) {
            throw new ConflictException("ORDER_NOT_PAYABLE", "This order can no longer be paid.");
        }
        CardPaymentSession session = stripe.prepare(order.getId(), order.getOrderNumber(), order.total());
        return new CardPaymentView(
                order.getOrderNumber(),
                order.total(),
                order.getPaymentDueAt(),
                session.alreadyPaid() ? null : session.clientSecret(),
                session.publishableKey(),
                session.alreadyPaid());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(UUID orderId) {
        orders.lockById(orderId);
    }

    /**
     * Marks the order as paid and tells the customer. If the order had been cancelled in the
     * meantime, it stays cancelled and the shop inbox is told, so the owner can refund.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void paymentSucceeded(PaymentSucceeded payment) {
        Order order = order(payment.orderId());
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("Payment confirmed for cancelled order {}; the shop inbox has been told", order.getId());
            outbox.enqueueEmail(emails.paymentForCancelledOrder(order, shopInbox));
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return; // Already paid.
        }
        if (!order.total().equals(payment.amount())) {
            throw new IllegalStateException("The payment amount differs from the order total");
        }
        order.markPaid(null, "Card payment confirmed by Stripe", clock.instant());
        outbox.enqueueEmail(emails.paymentReceived(order));
        audit.record(null, "ORDER_PAID", "ORDER", order.getId(),
                Map.of("orderNumber", order.getOrderNumber(), "provider", payment.provider().name()), null);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void paymentRefunded(PaymentRefunded refund) {
        Order order = order(refund.orderId());
        if (order.recordRefund(refund.full(), clock.instant())) {
            audit.record(null, "ORDER_REFUNDED", "ORDER", order.getId(),
                    Map.of("orderNumber", order.getOrderNumber(),
                            "refundedCents", refund.totalRefunded().amount(),
                            "full", refund.full()),
                    null);
        }
    }

    private Order order(UUID orderId) {
        return orders.lockById(orderId).orElseThrow(() -> new IllegalStateException("Payment for an unknown order"));
    }
}
