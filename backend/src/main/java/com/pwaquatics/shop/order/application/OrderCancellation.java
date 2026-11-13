package com.pwaquatics.shop.order.application;

import com.pwaquatics.shop.common.error.ConflictException;
import com.pwaquatics.shop.inventory.application.InventoryService;
import com.pwaquatics.shop.notification.application.OutboxService;
import com.pwaquatics.shop.order.domain.Order;
import com.pwaquatics.shop.order.domain.OrderStatus;
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
     * Cancelled by the shop: also allowed once paid (the owner refunds the money). An open card
     * payment is stopped first.
     *
     * @param reason internal reason
     * @param customerMessage shown to the customer and put in the email, or null
     * @param restock false when the animals cannot go back on sale (e.g. they did not survive)
     * @param notify whether to email the customer
     * @throws ConflictException {@code ORDER_NOT_CANCELLABLE} while Stripe is processing a card payment
     */
    @Transactional(propagation = Propagation.MANDATORY)
    void cancelByShop(
            Order order, String reason, String customerMessage, UUID changedBy, boolean restock, boolean notify) {
        if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            throw new ConflictException(
                    "INVALID_STATUS_TRANSITION", "An order cannot be cancelled once it has been shipped.");
        }
        if (order.getPaymentMethod() == PaymentMethod.STRIPE && !order.hasBeenPaid()
                && !stripe.cancelOpenPayment(order.getId())) {
            throw new ConflictException("ORDER_NOT_CANCELLABLE",
                    "Stripe is processing the card payment of this order. Try again once it is confirmed.");
        }
        order.cancel(reason, customerMessage, changedBy, clock.instant());
        if (restock) {
            inventory.releaseForOrder(order.getId(), order.stockUnitsByProduct());
        }
        if (notify) {
            outbox.enqueueEmail(emails.cancelledByShop(order, customerMessage));
        }
    }

    /**
     * Cancelled by the customer, or expired unpaid.
     *
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
