package com.pwaquatics.shop.order.api;

import com.pwaquatics.shop.common.api.PageResponse;
import com.pwaquatics.shop.order.application.OrderService;
import com.pwaquatics.shop.order.application.view.OrderDetailView;
import com.pwaquatics.shop.order.application.view.OrderSummaryView;
import com.pwaquatics.shop.security.ShopUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in customer's own orders (docs/API.md §2.6). Another customer's order is a 404. */
@RestController
@RequestMapping("/api/v1/account/orders")
@Tag(name = "Account")
class AccountOrderController {

    private static final String ORDER_NUMBER = "PW-[0-9]{4}-[0-9]{6,12}";

    private final OrderService orders;

    AccountOrderController(OrderService orders) {
        this.orders = orders;
    }

    @Operation(summary = "Order history, newest first")
    @GetMapping
    PageResponse<OrderSummaryView> list(
            @AuthenticationPrincipal ShopUserPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return orders.list(principal.userId(), page, size);
    }

    @Operation(summary = "One order with items, totals, addresses and status timeline")
    @GetMapping("/{orderNumber}")
    OrderDetailView detail(
            @AuthenticationPrincipal ShopUserPrincipal principal,
            @PathVariable @Pattern(regexp = ORDER_NUMBER) String orderNumber) {
        return orders.detail(principal.userId(), orderNumber);
    }

    @Operation(summary = "Cancel an order that has not been paid yet")
    @PostMapping("/{orderNumber}/cancel")
    OrderDetailView cancel(
            @AuthenticationPrincipal ShopUserPrincipal principal,
            @PathVariable @Pattern(regexp = ORDER_NUMBER) String orderNumber,
            HttpServletRequest http) {
        return orders.cancel(principal.userId(), orderNumber, http.getRemoteAddr());
    }
}
