package com.pwaquatics.shop.order.api;

import com.pwaquatics.shop.order.application.OrderPayments;
import com.pwaquatics.shop.order.application.view.CardPaymentView;
import com.pwaquatics.shop.order.application.view.PayPalCaptureView;
import com.pwaquatics.shop.order.application.view.PayPalPaymentView;
import com.pwaquatics.shop.security.ShopUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Card and PayPal payments for the signed-in customer's own orders (docs/API.md §2.7). The
 * amount is never part of a request: it is the stored order total.
 */
@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments")
class OrderPaymentController {

    private final OrderPayments payments;

    OrderPaymentController(OrderPayments payments) {
        this.payments = payments;
    }

    /** @param orderNumber one of the customer's own orders, placed for card payment */
    record CardPaymentRequest(@NotNull @Pattern(regexp = "PW-[0-9]{4}-[0-9]{6,12}") String orderNumber) {}

    /** @param orderNumber one of the customer's own orders, placed for payment with PayPal */
    record PayPalPaymentRequest(@NotNull @Pattern(regexp = "PW-[0-9]{4}-[0-9]{6,12}") String orderNumber) {}

    @Operation(summary = "Start (or resume) the card payment of an order; returns what Stripe.js needs")
    @PostMapping("/stripe/intents")
    CardPaymentView startCardPayment(
            @AuthenticationPrincipal ShopUserPrincipal principal, @Valid @RequestBody CardPaymentRequest request) {
        return payments.startCardPayment(principal.userId(), request.orderNumber());
    }

    @Operation(summary = "Start (or resume) the PayPal payment of an order; returns what PayPal's button needs")
    @PostMapping("/paypal/orders")
    PayPalPaymentView startPayPalPayment(
            @AuthenticationPrincipal ShopUserPrincipal principal, @Valid @RequestBody PayPalPaymentRequest request) {
        return payments.startPayPalPayment(principal.userId(), request.orderNumber());
    }

    @Operation(summary = "Take the PayPal payment the customer approved; the server reads the result from PayPal")
    @PostMapping("/paypal/captures")
    PayPalCaptureView capturePayPalPayment(
            @AuthenticationPrincipal ShopUserPrincipal principal, @Valid @RequestBody PayPalPaymentRequest request) {
        return payments.capturePayPalPayment(principal.userId(), request.orderNumber());
    }
}
