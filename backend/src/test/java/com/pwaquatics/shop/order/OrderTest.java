package com.pwaquatics.shop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pwaquatics.shop.common.error.ConflictException;
import com.pwaquatics.shop.order.domain.NewOrder;
import com.pwaquatics.shop.order.domain.NewOrderItem;
import com.pwaquatics.shop.order.domain.Order;
import com.pwaquatics.shop.order.domain.OrderAddress;
import com.pwaquatics.shop.order.domain.OrderNumber;
import com.pwaquatics.shop.order.domain.OrderStatus;
import com.pwaquatics.shop.order.domain.PaymentStatus;
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

    private static NewOrderItem item(UUID productId, String sku, int packSize, long price, int quantity, boolean live) {
        return new NewOrderItem(
                productId, UUID.randomUUID(), sku, "Product " + sku, packSize + " animals", "Species", "High", live,
                packSize, price, quantity, 1900);
    }
}
