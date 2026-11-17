package com.pwaquatics.shop.order.application;

import com.pwaquatics.shop.audit.application.Actor;
import com.pwaquatics.shop.audit.application.AuditService;
import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.common.error.ConflictException;
import com.pwaquatics.shop.common.error.NotFoundException;
import com.pwaquatics.shop.identity.application.UserDirectory;
import com.pwaquatics.shop.notification.application.OutboxService;
import com.pwaquatics.shop.order.application.view.AdminOrderSummaryView;
import com.pwaquatics.shop.order.application.view.AdminOrderView;
import com.pwaquatics.shop.order.domain.Order;
import com.pwaquatics.shop.order.domain.OrderNote;
import com.pwaquatics.shop.order.domain.OrderStatus;
import com.pwaquatics.shop.order.domain.OrderStatusChange;
import com.pwaquatics.shop.order.domain.Shipment;
import com.pwaquatics.shop.order.domain.ShipmentData;
import com.pwaquatics.shop.order.infrastructure.AdminOrderQueries;
import com.pwaquatics.shop.order.infrastructure.OrderNoteRepository;
import com.pwaquatics.shop.order.infrastructure.OrderRepository;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.payment.application.PayPalPayments;
import com.pwaquatics.shop.payment.application.PaymentRefunds;
import com.pwaquatics.shop.payment.application.PaymentRefunds.RefundRequest;
import com.pwaquatics.shop.payment.application.PaymentRefunds.RefundResult;
import com.pwaquatics.shop.payment.application.StripePayments;
import com.pwaquatics.shop.payment.application.view.PaymentSummaryView;
import com.pwaquatics.shop.payment.domain.PaymentMethod;
import com.pwaquatics.shop.shipping.application.ShippingService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order handling by the shop owner (docs/API.md §3.5): find orders, record bank transfers, move
 * orders through packing, shipping and delivery, cancel them and keep internal notes.
 *
 * <p>Every change locks the order, goes through the order's state machine
 * ({@link Order#transitionTo}), is recorded in the audit log and, where the customer should
 * know, queues an email.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderService {

    /** Statuses an order can be moved to with {@link #changeStatus}; the others have their own action. */
    public static final Set<OrderStatus> MANUAL_STATUSES = EnumSet.of(OrderStatus.PROCESSING, OrderStatus.DELIVERED);

    private final OrderRepository orders;
    private final AdminOrderQueries queries;
    private final OrderNoteRepository notes;
    private final OrderViews views;
    private final OrderCancellation cancellation;
    private final OrderEmails emails;
    private final OutboxService outbox;
    private final StripePayments stripe;
    private final PayPalPayments paypal;
    private final PaymentRefunds refunds;
    private final UserDirectory users;
    private final AuditService audit;
    private final AppProperties.Shipping shipping;
    private final Clock clock;

    AdminOrderService(
            OrderRepository orders,
            AdminOrderQueries queries,
            OrderNoteRepository notes,
            OrderViews views,
            OrderCancellation cancellation,
            OrderEmails emails,
            OutboxService outbox,
            StripePayments stripe,
            PayPalPayments paypal,
            PaymentRefunds refunds,
            UserDirectory users,
            AuditService audit,
            AppProperties properties,
            Clock clock) {
        this.orders = orders;
        this.queries = queries;
        this.notes = notes;
        this.views = views;
        this.cancellation = cancellation;
        this.emails = emails;
        this.outbox = outbox;
        this.stripe = stripe;
        this.paypal = paypal;
        this.refunds = refunds;
        this.users = users;
        this.audit = audit;
        this.shipping = properties.shipping();
        this.clock = clock;
    }

    /** All orders matching the filters; dates are days in the shop's time zone. */
    @Transactional(readOnly = true)
    public Page<AdminOrderSummaryView> list(AdminOrderQuery query) {
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
            throw new BusinessRuleException("RANGE_INVALID", "The first day must not be after the last day.");
        }
        return queries.search(
                query.q(),
                query.statuses() == null ? Set.of() : query.statuses(),
                query.paymentStatus(),
                query.from() == null ? null : startOf(query.from()),
                query.to() == null ? null : startOf(query.to().plusDays(1)),
                query.oldestFirst(),
                PageRequest.of(query.page(), query.size()));
    }

    @Transactional(readOnly = true)
    public AdminOrderView get(String orderNumber) {
        return view(orders.findByOrderNumber(orderNumber).orElseThrow(AdminOrderService::notFound));
    }

    /**
     * Records that the bank transfer for an order has arrived. Card payments are confirmed by
     * Stripe and cannot be marked by hand.
     *
     * @param paidOn the day the money arrived (today if null)
     * @throws ConflictException {@code PAID_BY_PROVIDER} for card orders,
     *     {@code INVALID_STATUS_TRANSITION} unless the order is waiting for payment
     * @throws BusinessRuleException {@code DATE_INVALID}
     */
    @Transactional
    public AdminOrderView markPaid(String orderNumber, LocalDate paidOn, String note, boolean notify, Actor actor) {
        Order order = lock(orderNumber);
        if (order.getPaymentMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new ConflictException(
                    "PAID_BY_PROVIDER", "Card payments are confirmed by Stripe and cannot be marked as paid by hand.");
        }
        Instant now = clock.instant();
        order.markPaid(actor.userId(), note, moment(paidOn, now), now);
        if (notify) {
            outbox.enqueueEmail(emails.paymentReceived(order));
        }
        record(actor, "ORDER_MARKED_PAID", order, Map.of("paidOn", String.valueOf(LocalDate.ofInstant(
                order.getPaidAt(), ShippingService.SHOP_ZONE))));
        return view(order);
    }

    /**
     * Moves an order to {@code PROCESSING} (being packed) or {@code DELIVERED}. Paying, shipping
     * and cancelling have their own actions.
     *
     * @throws ConflictException {@code INVALID_STATUS_TRANSITION}
     */
    @Transactional
    public AdminOrderView changeStatus(
            String orderNumber, OrderStatus status, String note, String customerMessage, boolean notify, Actor actor) {
        Order order = lock(orderNumber);
        Instant now = clock.instant();
        switch (status) {
            case PROCESSING -> {
                order.startProcessing(actor.userId(), note, customerMessage, now);
                if (notify) {
                    outbox.enqueueEmail(emails.processing(order, customerMessage));
                }
            }
            case DELIVERED -> {
                order.markDelivered(actor.userId(), note, customerMessage, now);
                if (notify) {
                    outbox.enqueueEmail(emails.delivered(order, customerMessage));
                }
            }
            default -> throw new ConflictException("INVALID_STATUS_TRANSITION",
                    "Use the actions for payment, shipping and cancelling to move an order to " + status + ".");
        }
        record(actor, "ORDER_STATUS_CHANGED", order, Map.of("status", status.name()));
        return view(order);
    }

    /**
     * Cancels an order that has not been shipped. Paid orders can be cancelled too; the owner
     * refunds the money afterwards ({@link #refund} for card and PayPal, a bank transfer otherwise).
     *
     * @param restock whether the animals go back on sale
     * @throws ConflictException {@code INVALID_STATUS_TRANSITION} once shipped;
     *     {@code ORDER_NOT_CANCELLABLE} while Stripe is processing a card payment
     */
    @Transactional
    public AdminOrderView cancel(
            String orderNumber, String reason, String customerMessage, boolean restock, boolean notify, Actor actor) {
        Order order = lock(orderNumber);
        boolean wasPaid = order.hasBeenPaid();
        cancellation.cancelByShop(order, reason, customerMessage, actor.userId(), restock, notify);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("restocked", restock);
        details.put("wasPaid", wasPaid);
        record(actor, "ORDER_CANCELLED_BY_SHOP", order, details);
        return view(order);
    }

    /**
     * Pays all or part of a card or PayPal payment back to the customer through the provider
     * that took it. Works for any order status (e.g. after a cancellation, or for animals that
     * arrived dead). Bank transfers are paid back by the owner's own transfer.
     *
     * <p>Repeating a request with the same {@code idempotencyKey} returns the order as it is,
     * without a second refund, email or audit entry.
     *
     * @param amountCents what to pay back
     * @param expectedRefundableCents what the owner saw as refundable when deciding
     * @param reason internal reason, or null
     * @throws ConflictException {@code NOT_REFUNDABLE}, {@code REFUNDS_CHANGED},
     *     {@code IDEMPOTENCY_KEY_REUSED}, {@code PAYMENT_METHOD_UNAVAILABLE} (provider not set up)
     * @throws BusinessRuleException {@code REFUND_AMOUNT_INVALID}, {@code REFUND_REFUSED}
     */
    @Transactional
    public AdminOrderView refund(
            String orderNumber,
            long amountCents,
            long expectedRefundableCents,
            String reason,
            String customerMessage,
            boolean notify,
            String idempotencyKey,
            Actor actor) {
        Order order = lock(orderNumber);
        PaymentMethod method = order.getPaymentMethod();
        if (method == PaymentMethod.BANK_TRANSFER) {
            throw new ConflictException("NOT_REFUNDABLE",
                    "Bank transfers are paid back by bank transfer; note it on the order.");
        }
        if (!providerAvailable(method)) {
            throw new ConflictException("PAYMENT_METHOD_UNAVAILABLE",
                    "Refunds need the payment provider's keys, which are not set up.");
        }
        String currency = order.total().currency();
        RefundResult result = refunds.refund(new RefundRequest(
                order.getId(),
                method,
                new Money(amountCents, currency),
                new Money(expectedRefundableCents, currency),
                idempotencyKey,
                reason,
                actor.userId()));
        if (!result.repeated()) {
            if (notify) {
                outbox.enqueueEmail(emails.refunded(order, result.amount(), customerMessage));
            }
            record(actor, "ORDER_REFUNDED_BY_SHOP", order, Map.of(
                    "amountCents", result.amount().amount(), "status", result.status().name()));
        }
        return view(order);
    }

    /** Adds an internal note (never shown to the customer). */
    @Transactional
    public AdminOrderView addNote(String orderNumber, String body, Actor actor) {
        Order order = orders.findByOrderNumber(orderNumber).orElseThrow(AdminOrderService::notFound);
        notes.save(new OrderNote(order.getId(), actor.userId(), body, clock.instant()));
        record(actor, "ORDER_NOTE_ADDED", order, Map.of());
        return view(order);
    }

    /**
     * Enters a parcel handed to the carrier. The first parcel marks the order as shipped and
     * (if {@code notify}) emails the customer the tracking link.
     *
     * @param shippedOn the day the parcel left (today if null)
     * @throws BusinessRuleException {@code CARRIER_UNKNOWN}, {@code DATE_INVALID}
     * @throws ConflictException {@code INVALID_STATUS_TRANSITION} unless paid, being packed or shipped
     */
    @Transactional
    public AdminOrderView addShipment(
            String orderNumber,
            String carrier,
            String trackingNumber,
            LocalDate shippedOn,
            String customerMessage,
            boolean notify,
            Actor actor) {
        Order order = lock(orderNumber);
        Instant now = clock.instant();
        Shipment shipment = order.ship(shipmentData(carrier, trackingNumber, moment(shippedOn, now)),
                actor.userId(), customerMessage, now);
        if (notify) {
            outbox.enqueueEmail(emails.shipped(order, shipment, customerMessage));
        }
        record(actor, "ORDER_SHIPPED", order, Map.of("shipmentId", shipment.getId().toString(),
                "carrier", shipment.getCarrier()));
        return view(order);
    }

    /** Corrects the carrier or tracking number of a parcel. No email is sent. */
    @Transactional
    public AdminOrderView correctShipment(
            String orderNumber, UUID shipmentId, String carrier, String trackingNumber, Actor actor) {
        Order order = lock(orderNumber);
        order.correctShipment(shipmentId, shipmentData(carrier, trackingNumber, null), clock.instant());
        record(actor, "SHIPMENT_CORRECTED", order, Map.of("shipmentId", shipmentId.toString()));
        return view(order);
    }

    /**
     * Removes a parcel entered by mistake (not the only one of a shipped order).
     *
     * @throws BusinessRuleException {@code LAST_SHIPMENT}
     */
    @Transactional
    public AdminOrderView removeShipment(String orderNumber, UUID shipmentId, Actor actor) {
        Order order = lock(orderNumber);
        order.removeShipment(shipmentId, clock.instant());
        record(actor, "SHIPMENT_REMOVED", order, Map.of("shipmentId", shipmentId.toString()));
        return view(order);
    }

    // ------------------------------------------------------------------ helpers

    private Order lock(String orderNumber) {
        return orders.lockByOrderNumber(orderNumber).orElseThrow(AdminOrderService::notFound);
    }

    private ShipmentData shipmentData(String carrierName, String trackingNumber, Instant shippedAt) {
        AppProperties.Carrier carrier = shipping.carrier(carrierName)
                .orElseThrow(() -> new BusinessRuleException("CARRIER_UNKNOWN", "Choose one of the listed carriers."));
        String number = trackingNumber.replaceAll("\\s+", "");
        return new ShipmentData(carrier.name(), number, carrier.trackingUrlFor(number), shippedAt);
    }

    /** A day chosen in the admin as a point in time: now for today, otherwise noon of that day (shop time). */
    private static Instant moment(LocalDate day, Instant now) {
        if (day == null || day.equals(LocalDate.ofInstant(now, ShippingService.SHOP_ZONE))) {
            return now;
        }
        return day.atTime(LocalTime.NOON).atZone(ShippingService.SHOP_ZONE).toInstant();
    }

    private static Instant startOf(LocalDate day) {
        return day.atStartOfDay(ShippingService.SHOP_ZONE).toInstant();
    }

    private void record(Actor actor, String action, Order order, Map<String, Object> extra) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("orderNumber", order.getOrderNumber());
        details.putAll(extra);
        audit.record(actor, action, "ORDER", order.getId(), details);
    }

    private AdminOrderView view(Order order) {
        List<OrderNote> orderNotes = notes.findByOrderIdOrderByCreatedAtAsc(order.getId());
        Set<UUID> people = new HashSet<>();
        order.getHistory().stream().map(OrderStatusChange::getChangedBy).filter(Objects::nonNull).forEach(people::add);
        orderNotes.stream().map(OrderNote::getAuthorId).filter(Objects::nonNull).forEach(people::add);
        Map<UUID, String> names = people.isEmpty() ? Map.of() : users.displayNames(people);

        OrderStatus status = order.getStatus();
        PaymentSummaryView payment = switch (order.getPaymentMethod()) {
            case STRIPE -> stripe.summary(order.getId()).orElse(null);
            case PAYPAL -> paypal.summary(order.getId()).orElse(null);
            case BANK_TRANSFER -> null;
        };
        return new AdminOrderView(
                views.detail(order),
                order.getUserId(),
                order.getCustomerEmail(),
                order.getCustomerLocale(),
                order.getPaidAt(),
                order.getCancelledAt(),
                order.getCancellationReason(),
                order.getHistory().stream()
                        .map(change -> new AdminOrderView.OrderHistoryEntry(
                                change.getFromStatus(),
                                change.getToStatus(),
                                change.getCreatedAt(),
                                change.getNote(),
                                change.getCustomerMessage(),
                                change.getChangedBy() == null ? null : names.get(change.getChangedBy())))
                        .toList(),
                orderNotes.stream()
                        .map(note -> new AdminOrderView.InternalNote(note.getId(), note.getBody(), note.getCreatedAt(),
                                note.getAuthorId() == null ? null : names.get(note.getAuthorId())))
                        .toList(),
                payment,
                shipping.carriers().stream().map(AppProperties.Carrier::name).toList(),
                new AdminOrderView.OrderActions(
                        status == OrderStatus.PENDING_PAYMENT && order.getPaymentMethod() == PaymentMethod.BANK_TRANSFER,
                        status == OrderStatus.PAID,
                        status == OrderStatus.PAID || status == OrderStatus.PROCESSING || status == OrderStatus.SHIPPED,
                        status == OrderStatus.SHIPPED,
                        status.canTransitionTo(OrderStatus.CANCELLED),
                        order.getShipments().size() > 1,
                        payment != null && payment.refundable().amount() > 0 && providerAvailable(payment.provider())));
    }

    private boolean providerAvailable(PaymentMethod method) {
        return switch (method) {
            case STRIPE -> stripe.isAvailable();
            case PAYPAL -> paypal.isAvailable();
            case BANK_TRANSFER -> false;
        };
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Order not found.");
    }
}
