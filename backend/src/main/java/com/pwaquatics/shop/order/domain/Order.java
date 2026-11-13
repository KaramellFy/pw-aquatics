package com.pwaquatics.shop.order.domain;

import com.pwaquatics.shop.common.error.ConflictException;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.common.persistence.AbstractEntity;
import com.pwaquatics.shop.payment.domain.PaymentMethod;
import com.pwaquatics.shop.pricing.domain.PriceCalculator;
import com.pwaquatics.shop.pricing.domain.PricedLine;
import com.pwaquatics.shop.pricing.domain.Totals;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A placed order. Items, prices and addresses are snapshots; the only things that change
 * afterwards are the status (through {@link #transitionTo}), the payment status and the
 * history, which records every status change.
 */
@Entity
@Table(name = "orders")
public class Order extends AbstractEntity {

    @Column(name = "order_number", nullable = false, unique = true, length = 20, updatable = false)
    private String orderNumber;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "customer_email", nullable = false, updatable = false, columnDefinition = "citext")
    private String customerEmail;

    @Column(name = "customer_locale", nullable = false, length = 5, updatable = false)
    private String customerLocale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30, updatable = false)
    private PaymentMethod paymentMethod;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "subtotal_cents", nullable = false, updatable = false)
    private long subtotalCents;

    @Column(name = "discount_cents", nullable = false, updatable = false)
    private long discountCents;

    @Column(name = "shipping_cents", nullable = false, updatable = false)
    private long shippingCents;

    @Column(name = "total_cents", nullable = false, updatable = false)
    private long totalCents;

    @Column(name = "tax_cents", nullable = false, updatable = false)
    private long taxCents;

    @Column(name = "shipping_method_code", nullable = false, length = 50, updatable = false)
    private String shippingMethodCode;

    @Column(name = "shipping_method_name", nullable = false, length = 100, updatable = false)
    private String shippingMethodName;

    @Column(name = "shipping_carrier", length = 50, updatable = false)
    private String shippingCarrier;

    @Column(name = "planned_dispatch_date")
    private LocalDate plannedDispatchDate;

    @Column(name = "customer_note", length = 1000, updatable = false)
    private String customerNote;

    @Column(name = "terms_accepted_at", nullable = false, updatable = false)
    private Instant termsAcceptedAt;

    @Column(name = "idempotency_key", length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "placed_at", nullable = false, updatable = false)
    private Instant placedAt;

    @Column(name = "payment_due_at")
    private Instant paymentDueAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<OrderItem> items = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "order_addresses", joinColumns = @JoinColumn(name = "order_id"))
    @MapKeyColumn(name = "type", length = 10)
    @MapKeyEnumerated(EnumType.STRING)
    private Map<AddressType, OrderAddress> addresses = new EnumMap<>(AddressType.class);

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private List<OrderStatusChange> history = new ArrayList<>();

    protected Order() {}

    // ---------------------------------------------------------------- creation

    /**
     * Creates a new order awaiting payment. Totals are calculated here from the item
     * snapshots and the shipping price, with the same calculator the cart and quote use.
     */
    public static Order place(NewOrder data, Instant now) {
        if (data.items().isEmpty()) {
            throw new IllegalArgumentException("An order needs at least one item");
        }
        Order order = new Order();
        order.orderNumber = data.orderNumber();
        order.userId = data.userId();
        order.customerEmail = data.customerEmail();
        order.customerLocale = data.customerLocale();
        order.paymentMethod = data.paymentMethod();
        order.currency = Money.EUR;
        order.shippingMethodCode = data.shippingMethodCode();
        order.shippingMethodName = data.shippingMethodName();
        order.shippingCarrier = data.shippingCarrier();
        order.plannedDispatchDate = data.plannedDispatchDate();
        order.customerNote = data.customerNote();
        order.idempotencyKey = data.idempotencyKey();
        order.termsAcceptedAt = now;
        order.placedAt = now;
        order.createdAt = now;
        order.updatedAt = now;
        order.paymentDueAt = data.paymentDueAt();
        order.paymentStatus = PaymentStatus.UNPAID;
        order.status = OrderStatus.PENDING_PAYMENT;

        List<PricedLine> lines = new ArrayList<>();
        for (NewOrderItem item : data.items()) {
            order.items.add(new OrderItem(order, order.items.size(), item));
            lines.add(new PricedLine(item.unitPriceCents(), item.quantity(), item.taxRateBps()));
        }
        Totals totals = PriceCalculator.totals(lines, data.shippingCents());
        order.subtotalCents = totals.subtotal().amount();
        order.discountCents = 0;
        order.shippingCents = totals.shipping().amount();
        order.totalCents = totals.total().amount();
        order.taxCents = totals.includedTax().amount();

        order.addresses.put(AddressType.SHIPPING, data.shippingAddress());
        order.addresses.put(AddressType.BILLING, data.billingAddress());
        order.history.add(new OrderStatusChange(order, null, OrderStatus.PENDING_PAYMENT, null, null, data.userId(), now));
        return order;
    }

    // ---------------------------------------------------------------- lifecycle

    /**
     * Moves the order to another status and records it in the history.
     *
     * @param changedBy the acting user, or null for the system
     * @param note internal note (not shown to the customer)
     * @param customerMessage optional message for the customer
     * @throws ConflictException {@code INVALID_STATUS_TRANSITION} if the lifecycle does not allow the move
     */
    public void transitionTo(OrderStatus next, UUID changedBy, String note, String customerMessage, Instant now) {
        if (!status.canTransitionTo(next)) {
            throw new ConflictException(
                    "INVALID_STATUS_TRANSITION", "An order cannot go from " + status + " to " + next + ".");
        }
        history.add(new OrderStatusChange(this, status, next, note, customerMessage, changedBy, now));
        status = next;
        updatedAt = now;
    }

    /** Records that the full amount has arrived and moves the order to {@code PAID}. */
    public void markPaid(UUID changedBy, String note, Instant now) {
        transitionTo(OrderStatus.PAID, changedBy, note, null, now);
        paymentStatus = PaymentStatus.PAID;
        paidAt = now;
        paymentDueAt = null;
    }

    /**
     * Cancels the order. Allowed until it has been shipped; the caller returns the stock
     * (see {@link #stockUnitsByProduct()}).
     */
    public void cancel(String reason, UUID changedBy, Instant now) {
        transitionTo(OrderStatus.CANCELLED, changedBy, reason, null, now);
        cancelledAt = now;
        cancellationReason = reason;
        paymentDueAt = null;
    }

    /**
     * Records money paid back to the customer (refunds are made in the payment provider's
     * dashboard). The status is left as it is: whether the order is cancelled is the owner's
     * decision.
     *
     * @return false if the order was never paid, so there is nothing to record
     */
    public boolean recordRefund(boolean full, Instant now) {
        if (paymentStatus == PaymentStatus.UNPAID || paymentStatus == PaymentStatus.FAILED) {
            return false;
        }
        paymentStatus = full ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
        updatedAt = now;
        return true;
    }

    /** Customers may cancel on their own only while nothing has been paid. */
    public boolean isCancellableByCustomer() {
        return status == OrderStatus.PENDING_PAYMENT;
    }

    /** Has the payment deadline of an unpaid order passed? */
    public boolean isPaymentOverdue(Instant now) {
        return status == OrderStatus.PENDING_PAYMENT && paymentDueAt != null && paymentDueAt.isBefore(now);
    }

    /** Animals this order holds per product (products that no longer exist are left out), in product id order. */
    public Map<UUID, Integer> stockUnitsByProduct() {
        Map<UUID, Integer> units = new TreeMap<>();
        for (OrderItem item : items) {
            if (item.getProductId() != null) {
                units.merge(item.getProductId(), item.stockUnits(), Math::addExact);
            }
        }
        return units;
    }

    public boolean containsLiveAnimals() {
        return items.stream().anyMatch(OrderItem::isLiveAnimal);
    }

    // ---------------------------------------------------------------- accessors

    public String getOrderNumber() {
        return orderNumber;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public String getCustomerLocale() {
        return customerLocale;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public Money subtotal() {
        return new Money(subtotalCents, currency);
    }

    public Money shipping() {
        return new Money(shippingCents, currency);
    }

    public Money total() {
        return new Money(totalCents, currency);
    }

    public Money includedTax() {
        return new Money(taxCents, currency);
    }

    public String getShippingMethodName() {
        return shippingMethodName;
    }

    public String getShippingCarrier() {
        return shippingCarrier;
    }

    public LocalDate getPlannedDispatchDate() {
        return plannedDispatchDate;
    }

    public String getCustomerNote() {
        return customerNote;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public Instant getPaymentDueAt() {
        return paymentDueAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public OrderAddress shippingAddress() {
        return addresses.get(AddressType.SHIPPING);
    }

    public OrderAddress billingAddress() {
        return addresses.get(AddressType.BILLING);
    }

    public List<OrderStatusChange> getHistory() {
        return history;
    }
}
