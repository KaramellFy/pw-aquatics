package com.pwaquatics.shop.order.application.view;

import com.pwaquatics.shop.order.domain.OrderStatus;
import com.pwaquatics.shop.payment.application.view.PaymentSummaryView;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An order as the shop owner sees it (docs/API.md §3.5): everything the customer sees, plus
 * the customer, internal notes, the full history, the payment and what can be done next.
 *
 * @param order the order as the customer sees it (items, totals, addresses, shipments)
 * @param customerId the customer's account, if it still exists
 * @param carriers carriers to choose from when entering a shipment
 * @param actions what the shop can do with the order right now (decided by the server)
 */
public record AdminOrderView(
        OrderDetailView order,
        @Nullable UUID customerId,
        String customerEmail,
        String customerLocale,
        @Nullable Instant paidAt,
        @Nullable Instant cancelledAt,
        @Nullable String cancellationReason,
        List<OrderHistoryEntry> history,
        List<InternalNote> notes,
        @Nullable PaymentSummaryView payment,
        List<String> carriers,
        OrderActions actions) {

    /**
     * @param note internal note
     * @param customerMessage message shown to the customer
     * @param changedBy who made the change; null for the system (e.g. Stripe's confirmation, expiry)
     */
    public record OrderHistoryEntry(
            @Nullable OrderStatus from,
            OrderStatus to,
            Instant at,
            @Nullable String note,
            @Nullable String customerMessage,
            @Nullable String changedBy) {}

    public record InternalNote(UUID id, String body, Instant at, @Nullable String author) {}

    /**
     * @param markPaid record a bank transfer (card payments are confirmed by Stripe)
     * @param startProcessing a paid order is being packed
     * @param ship enter a parcel (the first one marks the order as shipped)
     * @param markDelivered the parcels have arrived
     * @param cancel cancel before shipping
     * @param removeShipment remove a parcel entered by mistake (only while there is more than one)
     */
    public record OrderActions(
            boolean markPaid,
            boolean startProcessing,
            boolean ship,
            boolean markDelivered,
            boolean cancel,
            boolean removeShipment) {}
}
