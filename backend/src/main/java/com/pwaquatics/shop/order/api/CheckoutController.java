package com.pwaquatics.shop.order.api;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.order.application.CheckoutService;
import com.pwaquatics.shop.order.application.PlaceOrder;
import com.pwaquatics.shop.order.application.view.PlacedOrderView;
import com.pwaquatics.shop.order.application.view.QuoteView;
import com.pwaquatics.shop.security.ShopUserPrincipal;
import com.pwaquatics.shop.shipping.application.view.ShippingOptions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checkout for signed-in customers (docs/API.md §2.5). The customer and their cart always
 * come from the session, never from the request.
 */
@RestController
@RequestMapping("/api/v1/checkout")
@Tag(name = "Checkout")
class CheckoutController {

    private final CheckoutService checkout;

    CheckoutController(CheckoutService checkout) {
        this.checkout = checkout;
    }

    @Operation(summary = "Shipping methods for the cart and a destination country")
    @GetMapping("/shipping-methods")
    ShippingOptions shippingMethods(
            @AuthenticationPrincipal ShopUserPrincipal principal,
            @RequestParam @Pattern(regexp = "[A-Z]{2}") String country) {
        return checkout.shippingMethods(principal.userId(), country);
    }

    @Operation(summary = "Price breakdown for the cart, an address and a shipping method")
    @PostMapping("/quote")
    QuoteView quote(@AuthenticationPrincipal ShopUserPrincipal principal, @Valid @RequestBody QuoteRequest request) {
        return checkout.quote(principal.userId(), request.shippingAddressId(), request.shippingMethodCode());
    }

    @Operation(summary = "Place the order (repeating a request with the same Idempotency-Key returns the same order)")
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    PlacedOrderView placeOrder(
            @AuthenticationPrincipal ShopUserPrincipal principal,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9_-]{16,100}") String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request,
            HttpServletRequest http) {
        String note = request.customerNote() == null || request.customerNote().isBlank()
                ? null
                : request.customerNote().strip();
        return checkout.placeOrder(
                principal.userId(),
                new PlaceOrder(
                        request.shippingAddressId(),
                        request.billingAddressId(),
                        request.shippingMethodCode(),
                        request.paymentMethod(),
                        note,
                        request.acceptedLiveAnimalConditions(),
                        new Money(request.expectedTotal().amount(), request.expectedTotal().currency())),
                idempotencyKey,
                http.getRemoteAddr());
    }
}
