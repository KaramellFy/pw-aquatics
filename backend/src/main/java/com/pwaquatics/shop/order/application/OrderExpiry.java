package com.pwaquatics.shop.order.application;

import com.pwaquatics.shop.order.domain.Order;
import com.pwaquatics.shop.order.infrastructure.OrderRepository;
import com.pwaquatics.shop.payment.domain.PaymentMethod;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancels orders whose payment deadline has passed, so the reserved animals go back on sale.
 * Each order is handled in its own transaction: one failure never blocks the others.
 */
@Component
public class OrderExpiry {

    private final OrderRepository orders;
    private final OrderCancellation cancellation;
    private final Clock clock;

    OrderExpiry(OrderRepository orders, OrderCancellation cancellation, Clock clock) {
        this.orders = orders;
        this.cancellation = cancellation;
        this.clock = clock;
    }

    /** Ids of up to {@code limit} unpaid orders that are overdue now. */
    @Transactional(readOnly = true)
    public List<UUID> overdueOrderIds(int limit) {
        return orders.findOverdueIds(clock.instant(), PageRequest.of(0, limit));
    }

    /**
     * Cancels one order if it is still unpaid and overdue (it may have been paid or cancelled
     * since it was listed). A card payment that Stripe is still processing keeps the order; the
     * next run looks at it again.
     *
     * @return whether the order was cancelled
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expire(UUID orderId) {
        Order order = orders.lockById(orderId).orElse(null);
        if (order == null || !order.isPaymentOverdue(clock.instant())) {
            return false;
        }
        String reason = order.getPaymentMethod() == PaymentMethod.STRIPE
                ? "Card payment not completed in time"
                : "Payment not received by the due date";
        return cancellation.cancel(order, reason, null, true);
    }
}
