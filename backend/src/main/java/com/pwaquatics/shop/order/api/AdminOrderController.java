package com.pwaquatics.shop.order.api;

import static com.pwaquatics.shop.common.text.Texts.trimToNull;

import com.pwaquatics.shop.audit.application.Actor;
import com.pwaquatics.shop.common.api.PageResponse;
import com.pwaquatics.shop.order.application.AdminOrderQuery;
import com.pwaquatics.shop.order.application.AdminOrderService;
import com.pwaquatics.shop.order.application.view.AdminOrderSummaryView;
import com.pwaquatics.shop.order.application.view.AdminOrderView;
import com.pwaquatics.shop.order.domain.OrderStatus;
import com.pwaquatics.shop.order.domain.PaymentStatus;
import com.pwaquatics.shop.security.ShopUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Order handling for the shop owner (docs/API.md §3.5). Role ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/orders")
@Tag(name = "Admin: orders")
class AdminOrderController {

    private static final String ORDER_NUMBER = "PW-[0-9]{4}-[0-9]{6,12}";
    private static final String TRACKING_NUMBER = "[A-Za-z0-9 ]{4,40}";

    private final AdminOrderService orders;

    AdminOrderController(AdminOrderService orders) {
        this.orders = orders;
    }

    record MarkPaidRequest(LocalDate paidOn, @Size(max = 1000) String note, Boolean notifyCustomer) {}

    record OrderStatusRequest(
            @NotNull OrderStatus status,
            @Size(max = 1000) String note,
            @Size(max = 1000) String customerMessage,
            Boolean notifyCustomer) {}

    /**
     * @param reason internal reason (required)
     * @param restock whether the animals go back on sale (default true)
     */
    record CancelRequest(
            @NotBlank @Size(max = 500) String reason,
            @Size(max = 1000) String customerMessage,
            Boolean restock,
            Boolean notifyCustomer) {}

    record NoteRequest(@NotBlank @Size(max = 2000) String body) {}

    record ShipmentRequest(
            @NotBlank @Size(max = 50) String carrier,
            @NotBlank @Pattern(regexp = TRACKING_NUMBER) String trackingNumber,
            LocalDate shippedOn,
            @Size(max = 1000) String customerMessage,
            Boolean notifyCustomer) {}

    record ShipmentCorrection(
            @NotBlank @Size(max = 50) String carrier,
            @NotBlank @Pattern(regexp = TRACKING_NUMBER) String trackingNumber) {}

    @Operation(summary = "Orders, newest first (or oldest first); search by order number, email or name")
    @GetMapping
    PageResponse<AdminOrderSummaryView> list(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) List<OrderStatus> status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean oldestFirst,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        Set<OrderStatus> statuses = status == null || status.isEmpty() ? Set.of() : EnumSet.copyOf(status);
        return PageResponse.of(orders.list(
                new AdminOrderQuery(trimToNull(q), statuses, paymentStatus, from, to, oldestFirst, page, size)));
    }

    @Operation(summary = "One order with customer, history, internal notes, payment and possible actions")
    @GetMapping("/{orderNumber}")
    AdminOrderView get(@PathVariable @Pattern(regexp = ORDER_NUMBER) String orderNumber) {
        return orders.get(orderNumber);
    }

    @Operation(summary = "A bank transfer has arrived (card payments are confirmed by Stripe)")
    @PostMapping("/{orderNumber}/mark-paid")
    AdminOrderView markPaid(
            @PathVariable @Pattern(regexp = ORDER_NUMBER) String orderNumber,
            @Valid @RequestBody MarkPaidRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return orders.markPaid(orderNumber, request.paidOn(), trimToNull(request.note()),
                !Boolean.FALSE.equals(request.notifyCustomer()), actor(principal, http));
    }

    @Operation(summary = "Move an order to PROCESSING (being packed) or DELIVERED")
    @PostMapping("/{orderNumber}/status")
    AdminOrderView changeStatus(
            @PathVariable @Pattern(regexp = ORDER_NUMBER) String orderNumber,
            @Valid @RequestBody OrderStatusRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return orders.changeStatus(orderNumber, request.status(), trimToNull(request.note()),
                trimToNull(request.customerMessage()), !Boolean.FALSE.equals(request.notifyCustomer()),
                actor(principal, http));
    }

    @Operation(summary = "Cancel an order that has not been shipped")
    @PostMapping("/{orderNumber}/cancel")
    AdminOrderView cancel(
            @PathVariable @Pattern(regexp = ORDER_NUMBER) String orderNumber,
            @Valid @RequestBody CancelRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return orders.cancel(orderNumber, request.reason().strip(), trimToNull(request.customerMessage()),
                !Boolean.FALSE.equals(request.restock()), !Boolean.FALSE.equals(request.notifyCustomer()),
                actor(principal, http));
    }

    @Operation(summary = "Add an internal note (never shown to the customer)")
    @PostMapping("/{orderNumber}/notes")
    AdminOrderView addNote(
            @PathVariable @Pattern(regexp = ORDER_NUMBER) String orderNumber,
            @Valid @RequestBody NoteRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return orders.addNote(orderNumber, request.body().strip(), actor(principal, http));
    }

    @Operation(summary = "Enter a parcel; the first one marks the order as shipped and emails the tracking link")
    @PostMapping("/{orderNumber}/shipments")
    AdminOrderView addShipment(
            @PathVariable @Pattern(regexp = ORDER_NUMBER) String orderNumber,
            @Valid @RequestBody ShipmentRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return orders.addShipment(orderNumber, request.carrier(), request.trackingNumber(), request.shippedOn(),
                trimToNull(request.customerMessage()), !Boolean.FALSE.equals(request.notifyCustomer()),
                actor(principal, http));
    }

    @Operation(summary = "Correct a parcel's carrier or tracking number")
    @PutMapping("/{orderNumber}/shipments/{shipmentId}")
    AdminOrderView correctShipment(
            @PathVariable @Pattern(regexp = ORDER_NUMBER) String orderNumber,
            @PathVariable UUID shipmentId,
            @Valid @RequestBody ShipmentCorrection request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return orders.correctShipment(orderNumber, shipmentId, request.carrier(), request.trackingNumber(),
                actor(principal, http));
    }

    @Operation(summary = "Remove a parcel entered by mistake (not the only one)")
    @DeleteMapping("/{orderNumber}/shipments/{shipmentId}")
    AdminOrderView removeShipment(
            @PathVariable @Pattern(regexp = ORDER_NUMBER) String orderNumber,
            @PathVariable UUID shipmentId,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return orders.removeShipment(orderNumber, shipmentId, actor(principal, http));
    }

    private static Actor actor(ShopUserPrincipal principal, HttpServletRequest http) {
        return new Actor(principal.userId(), http.getRemoteAddr());
    }
}
