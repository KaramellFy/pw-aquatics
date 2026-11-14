package com.pwaquatics.shop.order.application;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.order.application.view.OrderAddressView;
import com.pwaquatics.shop.order.application.view.OrderDetailView;
import com.pwaquatics.shop.order.application.view.OrderItemView;
import com.pwaquatics.shop.order.application.view.OrderStatusEntryView;
import com.pwaquatics.shop.order.application.view.OrderSummaryView;
import com.pwaquatics.shop.order.application.view.OrderTotalsView;
import com.pwaquatics.shop.order.application.view.PlacedOrderView;
import com.pwaquatics.shop.order.application.view.ShipmentView;
import com.pwaquatics.shop.order.domain.Order;
import com.pwaquatics.shop.order.domain.OrderAddress;
import com.pwaquatics.shop.order.domain.OrderItem;
import com.pwaquatics.shop.order.domain.OrderStatus;
import com.pwaquatics.shop.order.domain.Shipment;
import com.pwaquatics.shop.payment.application.BankTransferPayments;
import com.pwaquatics.shop.payment.application.StripePayments;
import com.pwaquatics.shop.payment.application.view.PaymentInstructionsView;
import com.pwaquatics.shop.payment.domain.PaymentMethod;
import java.time.Clock;
import org.springframework.stereotype.Component;

/** Turns orders into the views sent to customers. Must be used inside a transaction (lazy collections). */
@Component
class OrderViews {

    private final BankTransferPayments bankTransfer;
    private final StripePayments stripe;
    private final Clock clock;

    OrderViews(BankTransferPayments bankTransfer, StripePayments stripe, Clock clock) {
        this.bankTransfer = bankTransfer;
        this.stripe = stripe;
        this.clock = clock;
    }

    PlacedOrderView placed(Order order) {
        return new PlacedOrderView(
                order.getOrderNumber(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getPaymentMethod(),
                order.total(),
                order.getPaymentDueAt(),
                paymentInstructions(order));
    }

    OrderSummaryView summary(Order order) {
        return new OrderSummaryView(
                order.getOrderNumber(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getPlacedAt(),
                order.total(),
                order.getItems().stream().mapToInt(OrderItem::getQuantity).sum(),
                order.getItems().stream().map(OrderItem::getProductName).distinct().toList());
    }

    OrderDetailView detail(Order order) {
        return new OrderDetailView(
                order.getOrderNumber(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getPaymentMethod(),
                order.getPlacedAt(),
                order.getPaymentDueAt(),
                order.getPlannedDispatchDate(),
                order.getItems().stream().map(OrderViews::item).toList(),
                new OrderTotalsView(order.subtotal(), order.shipping(), order.total(), order.includedTax()),
                order.getShippingMethodName(),
                order.getShippingCarrier(),
                address(order.shippingAddress()),
                address(order.billingAddress()),
                order.getCustomerNote(),
                order.containsLiveAnimals(),
                order.getHistory().stream()
                        .map(change -> new OrderStatusEntryView(
                                change.getToStatus(), change.getCreatedAt(), change.getCustomerMessage()))
                        .toList(),
                paymentInstructions(order),
                order.isCancellableByCustomer(),
                isPayableByCard(order),
                order.getShipments().stream().map(OrderViews::shipment).toList());
    }

    static ShipmentView shipment(Shipment shipment) {
        return new ShipmentView(
                shipment.getId(),
                shipment.getCarrier(),
                shipment.getTrackingNumber(),
                shipment.getTrackingUrl(),
                shipment.getShippedAt(),
                shipment.getDeliveredAt());
    }

    /** A card order can be paid until its payment deadline, while Stripe is set up. */
    boolean isPayableByCard(Order order) {
        return order.getStatus() == OrderStatus.PENDING_PAYMENT
                && order.getPaymentMethod() == PaymentMethod.STRIPE
                && !order.isPaymentOverdue(clock.instant())
                && stripe.isAvailable();
    }

    /** Bank details are only handed out while the order is actually waiting for a transfer. */
    PaymentInstructionsView paymentInstructions(Order order) {
        boolean waitingForTransfer = order.getStatus() == OrderStatus.PENDING_PAYMENT
                && order.getPaymentMethod() == PaymentMethod.BANK_TRANSFER
                && bankTransfer.isAvailable();
        return waitingForTransfer ? bankTransfer.instructions(order.getOrderNumber(), order.total()) : null;
    }

    private static OrderItemView item(OrderItem item) {
        return new OrderItemView(
                item.getSku(),
                item.getProductName(),
                item.getVariantName(),
                item.getSpeciesName(),
                item.getGradeName(),
                item.getQuantity(),
                Money.eur(item.getUnitPriceCents()),
                Money.eur(item.getLineTotalCents()));
    }

    private static OrderAddressView address(OrderAddress address) {
        return new OrderAddressView(
                address.getFirstName(),
                address.getLastName(),
                address.getCompany(),
                address.getStreet(),
                address.getHouseNumber(),
                address.getAddressLine2(),
                address.getPostalCode(),
                address.getCity(),
                address.getCountryCode(),
                address.getPhone());
    }
}
