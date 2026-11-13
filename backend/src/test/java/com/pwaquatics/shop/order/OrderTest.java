package com.pwaquatics.shop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.common.error.ConflictException;
import com.pwaquatics.shop.common.error.NotFoundException;
import com.pwaquatics.shop.order.domain.NewOrder;
import com.pwaquatics.shop.order.domain.NewOrderItem;
import com.pwaquatics.shop.order.domain.Order;
import com.pwaquatics.shop.order.domain.OrderAddress;
import com.pwaquatics.shop.order.domain.OrderNumber;
import com.pwaquatics.shop.order.domain.OrderStatus;
import com.pwaquatics.shop.order.domain.PaymentStatus;
import com.pwaquatics.shop.order.domain.Shipment;
import com.pwaquatics.shop.order.domain.ShipmentData;
import com.pwaquatics.shop.payment.domain.PaymentMethod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();
    private static final UUID PRODUCT_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID PRODUCT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void aNewOrderCalculatesItsTotalsFromTheItemSnapshots() {
        Order order = newOrder();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        // 3 × 3.00 + 1 × 25.00 + 2 × 5.00 = 44.00, plus 9.90 shipping.
        assertThat(order.subtotal().amount()).isEqualTo(4400);
        assertThat(order.shipping().amount()).isEqualTo(990);
        assertThat(order.total().amount()).isEqualTo(5390);
        // 53.90 gross at 19 % contains 8.61 VAT (8.6058…).
        assertThat(order.includedTax().amount()).isEqualTo(861);
        assertThat(order.getItems()).extracting("lineTotalCents").containsExactly(900L, 2500L, 1000L);
        assertThat(order.getHistory()).hasSize(1);
        assertThat(order.getHistory().getFirst().getToStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.containsLiveAnimals()).isTrue();
    }

    @Test
    void packSizesOfOneProductAddUpToTheAnimalsHeld() {
        // 3 singles + one pack of ten of product A, 2 of product B.
        assertThat(newOrder().stockUnitsByProduct()).containsExactly(
                java.util.Map.entry(PRODUCT_A, 13), java.util.Map.entry(PRODUCT_B, 2));
    }

    @Test
    void theHappyPathRunsFromPaymentToDelivery() {
        Order order = newOrder();
        order.markPaid(ADMIN, "Transfer received", NOW.plusSeconds(60));
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getPaymentDueAt()).isNull();
        assertThat(order.isCancellableByCustomer()).isFalse();

        order.transitionTo(OrderStatus.PROCESSING, ADMIN, null, null, NOW.plusSeconds(120));
        order.transitionTo(OrderStatus.SHIPPED, ADMIN, null, "Packed with a heat pack.", NOW.plusSeconds(180));
        order.transitionTo(OrderStatus.DELIVERED, null, null, null, NOW.plusSeconds(240));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getHistory()).extracting("toStatus").containsExactly(
                OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.SHIPPED,
                OrderStatus.DELIVERED);
        assertThat(order.getHistory().get(3).getCustomerMessage()).isEqualTo("Packed with a heat pack.");
    }

    @Test
    void illegalStatusTransitionsAreRejectedAndLeaveTheOrderUntouched() {
        Order order = newOrder();
        for (OrderStatus illegal : List.of(OrderStatus.PROCESSING, OrderStatus.SHIPPED, OrderStatus.DELIVERED,
                OrderStatus.PENDING_PAYMENT)) {
            assertThatThrownBy(() -> order.transitionTo(illegal, ADMIN, null, null, NOW))
                    .isInstanceOf(ConflictException.class)
                    .extracting("code").isEqualTo("INVALID_STATUS_TRANSITION");
        }
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getHistory()).hasSize(1);

        // Shipped orders cannot be cancelled; finished orders cannot change at all.
        order.markPaid(ADMIN, null, NOW);
        order.transitionTo(OrderStatus.PROCESSING, ADMIN, null, null, NOW);
        order.transitionTo(OrderStatus.SHIPPED, ADMIN, null, null, NOW);
        assertThatThrownBy(() -> order.cancel("too late", ADMIN, NOW)).isInstanceOf(ConflictException.class);
        assertThat(OrderStatus.DELIVERED.isFinal()).isTrue();
        assertThat(OrderStatus.CANCELLED.allowedNext()).isEmpty();
    }

    @Test
    void anUnpaidOrderCanBeCancelledAndExpires() {
        Order order = newOrder();
        assertThat(order.isCancellableByCustomer()).isTrue();
        assertThat(order.isPaymentOverdue(NOW)).isFalse();
        assertThat(order.isPaymentOverdue(NOW.plusSeconds(8 * 24 * 3600))).isTrue();

        order.cancel("Cancelled by the customer", CUSTOMER, NOW.plusSeconds(30));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(order.isCancellableByCustomer()).isFalse();
        assertThat(order.isPaymentOverdue(NOW.plusSeconds(8 * 24 * 3600))).isFalse();
        assertThatThrownBy(() -> order.cancel("again", CUSTOMER, NOW)).isInstanceOf(ConflictException.class);
    }

    @Test
    void refundsAreRecordedOnlyForPaidOrdersAndLeaveTheStatusAlone() {
        Order order = newOrder();
        assertThat(order.recordRefund(true, NOW)).isFalse();
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);

        order.markPaid(null, "Card payment confirmed by Stripe", NOW);
        assertThat(order.recordRefund(false, NOW.plusSeconds(60))).isTrue();
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(order.recordRefund(true, NOW.plusSeconds(120))).isTrue();
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void theFirstParcelShipsAPaidOrderThroughPacking() {
        Order order = newOrder();
        order.markPaid(ADMIN, null, NOW.plusSeconds(60));

        Shipment parcel = order.ship(parcel("JJD000111", NOW.plusSeconds(120)), ADMIN, "Two heat packs.",
                NOW.plusSeconds(120));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        // Packing was not recorded separately, so the history passes through it.
        assertThat(order.getHistory()).extracting("toStatus").containsExactly(
                OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.SHIPPED);
        assertThat(order.getHistory().getLast().getCustomerMessage()).isEqualTo("Two heat packs.");
        assertThat(order.getShipments()).containsExactly(parcel);
        assertThat(parcel.getTrackingUrl()).endsWith("JJD000111");

        // A second parcel of a split order is only added.
        order.ship(parcel("JJD000222", NOW.plusSeconds(180)), ADMIN, null, NOW.plusSeconds(180));
        assertThat(order.getShipments()).hasSize(2);
        assertThat(order.getHistory()).hasSize(4);

        order.markDelivered(ADMIN, null, null, NOW.plusSeconds(3600));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getShipments()).extracting(Shipment::getDeliveredAt)
                .containsOnly(NOW.plusSeconds(3600));
        assertThatThrownBy(() -> order.ship(parcel("JJD000333", NOW.plusSeconds(3700)), ADMIN, null,
                        NOW.plusSeconds(3700)))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("INVALID_STATUS_TRANSITION");
    }

    @Test
    void unpaidOrCancelledOrdersCannotBeShippedAndDatesMustMakeSense() {
        Order unpaid = newOrder();
        assertThatThrownBy(() -> unpaid.ship(parcel("JJD1", NOW), ADMIN, null, NOW))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("INVALID_STATUS_TRANSITION");
        assertThat(unpaid.getShipments()).isEmpty();
        assertThat(unpaid.getHistory()).hasSize(1);

        // Paid before the order existed, or in the future.
        assertThatThrownBy(() -> unpaid.markPaid(ADMIN, null, NOW.minusSeconds(3 * 86_400), NOW))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("DATE_INVALID");
        assertThatThrownBy(() -> unpaid.markPaid(ADMIN, null, NOW.plusSeconds(60), NOW))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("DATE_INVALID");
        assertThat(unpaid.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        // A transfer that arrived the evening before the order was placed is accepted.
        unpaid.markPaid(ADMIN, null, NOW.minusSeconds(3600), NOW.plusSeconds(60));
        assertThat(unpaid.getPaidAt()).isEqualTo(NOW.minusSeconds(3600));
        assertThat(unpaid.hasBeenPaid()).isTrue();

        assertThatThrownBy(() -> unpaid.ship(parcel("JJD1", NOW.plusSeconds(86_400 * 2)), ADMIN, null,
                        NOW.plusSeconds(60)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("DATE_INVALID");
        assertThat(unpaid.getStatus()).isEqualTo(OrderStatus.PAID);

        Order cancelled = newOrder();
        cancelled.cancel("Out of stock", "Sorry, the animals are not ready.", ADMIN, NOW);
        assertThat(cancelled.getCancellationReason()).isEqualTo("Out of stock");
        assertThat(cancelled.getHistory().getLast().getCustomerMessage()).isEqualTo("Sorry, the animals are not ready.");
        assertThatThrownBy(() -> cancelled.ship(parcel("JJD1", NOW), ADMIN, null, NOW))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void parcelsCanBeCorrectedAndRemovedButTheLastOneStays() {
        Order order = newOrder();
        order.markPaid(ADMIN, null, NOW);
        Shipment first = order.ship(parcel("WRONG1", NOW), ADMIN, null, NOW);

        assertThatThrownBy(() -> order.removeShipment(first.getId(), NOW))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("LAST_SHIPMENT");
        order.correctShipment(first.getId(), parcel("JJD42", null), NOW.plusSeconds(60));
        assertThat(first.getTrackingNumber()).isEqualTo("JJD42");
        assertThat(first.getTrackingUrl()).endsWith("JJD42");
        assertThat(first.getShippedAt()).isEqualTo(NOW);

        Shipment second = order.ship(parcel("JJD43", NOW), ADMIN, null, NOW);
        order.removeShipment(second.getId(), NOW);
        assertThat(order.getShipments()).containsExactly(first);
        assertThatThrownBy(() -> order.correctShipment(UUID.randomUUID(), parcel("X", null), NOW))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void orderNumbersCarryTheYearAndAPaddedSequence() {
        assertThat(OrderNumber.of(2027, 123)).isEqualTo("PW-2027-000123");
        assertThat(OrderNumber.of(2027, 1_234_567)).isEqualTo("PW-2027-1234567");
        assertThatThrownBy(() -> OrderNumber.of(2027, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anOrderNeedsAtLeastOneItem() {
        NewOrder template = data(List.of());
        assertThatThrownBy(() -> Order.place(template, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Order newOrder() {
        return Order.place(
                data(List.of(
                        item(PRODUCT_A, "A-1", 1, 300, 3, true),
                        item(PRODUCT_A, "A-10", 10, 2500, 1, true),
                        item(PRODUCT_B, "B-1", 1, 500, 2, false))),
                NOW);
    }

    private static NewOrder data(List<NewOrderItem> items) {
        OrderAddress address =
                new OrderAddress("Anna", "Schmidt", null, "Hauptstraße", "12a", null, "10115", "Berlin", "DE", null);
        return new NewOrder(
                "PW-2026-000001", CUSTOMER, "anna@example.com", "en", PaymentMethod.BANK_TRANSFER, items, 990,
                "DHL_LIVE_DE", "DHL Express", "DHL", LocalDate.of(2026, 11, 16), address, address, null, "key-1",
                NOW.plusSeconds(7 * 24 * 3600));
    }

    private static ShipmentData parcel(String trackingNumber, Instant shippedAt) {
        return new ShipmentData("DHL", trackingNumber, "https://tracking.example/?id=" + trackingNumber, shippedAt);
    }

    private static NewOrderItem item(UUID productId, String sku, int packSize, long price, int quantity, boolean live) {
        return new NewOrderItem(
                productId, UUID.randomUUID(), sku, "Product " + sku, packSize + " animals", "Species", "High", live,
                packSize, price, quantity, 1900);
    }
}
