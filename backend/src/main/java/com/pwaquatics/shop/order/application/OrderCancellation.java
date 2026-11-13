package com.pwaquatics.shop.order.application;

import com.pwaquatics.shop.inventory.application.InventoryService;
import com.pwaquatics.shop.notification.application.OutboxService;
import com.pwaquatics.shop.order.domain.Order;
import com.pwaquatics.shop.payment.application.StripePayments;
import com.pwaquatics.shop.payment.domain.PaymentMethod;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancels an unshipped order: stops an open card payment, changes the status, puts the animals
 * back into stock and emails the customer — all in the caller's transaction. The caller must
 * hold the lock on the order.
 */
@Component
class OrderCancellation {

    private final StripePayments stripe;
    private final InventoryService inventory;
    private final OutboxService outbox;
    private final OrderEmails emails;
    private final Clock clock;

    OrderCancellation(
            StripePayments stripe, InventoryService inventory, OutboxService outbox, OrderEmails emails, Clock clock) {
        this.stripe = stripe;
        this.inventory = inventory;
        this.outbox = outbox;
        this.emails = emails;
        this.clock = clock;
    }

    /**
     * @param changedBy the acting user, or null for the system
     * @param paymentMissed true when the order expired unpaid (decides the wording of the email)
     * @return false if the order was left as it is because its card payment can no longer be
     *     stopped: Stripe already has the money or is processing it, and its webhook will mark
     *     the order as paid
     */
    @Transactional(propagation = Propagation.MANDATORY)
    boolean cancel(Order order, String reason, UUID changedBy, boolean paymentMissed) {
        // First, so that the customer cannot pay for an order that no longer exists.
        if (order.getPaymentMethod() == PaymentMethod.STRIPE && !stripe.cancelOpenPayment(order.getId())) {
            return false;
        }
        order.cancel(reason, changedBy, clock.instant());
        inventory.releaseForOrder(order.getId(), order.stockUnitsByProduct());
        outbox.enqueueEmail(emails.cancelled(order, paymentMissed));
        return true;
    }
}
