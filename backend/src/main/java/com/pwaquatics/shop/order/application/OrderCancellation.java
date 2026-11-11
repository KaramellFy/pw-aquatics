package com.pwaquatics.shop.order.application;

import com.pwaquatics.shop.inventory.application.InventoryService;
import com.pwaquatics.shop.notification.application.OutboxService;
import com.pwaquatics.shop.order.domain.Order;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancels an unshipped order: status change, animals back into stock and an email to the
 * customer — all in the caller's transaction. The caller must hold the lock on the order.
 */
@Component
class OrderCancellation {

    private final InventoryService inventory;
    private final OutboxService outbox;
    private final OrderEmails emails;
    private final Clock clock;

    OrderCancellation(InventoryService inventory, OutboxService outbox, OrderEmails emails, Clock clock) {
        this.inventory = inventory;
        this.outbox = outbox;
        this.emails = emails;
        this.clock = clock;
    }

    /**
     * @param changedBy the acting user, or null for the system
     * @param paymentMissed true when the order expired unpaid (decides the wording of the email)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    void cancel(Order order, String reason, UUID changedBy, boolean paymentMissed) {
        order.cancel(reason, changedBy, clock.instant());
        inventory.releaseForOrder(order.getId(), order.stockUnitsByProduct());
        outbox.enqueueEmail(emails.cancelled(order, paymentMissed));
    }
}
