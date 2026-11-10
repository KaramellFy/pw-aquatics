package com.pwaquatics.shop.cart.api;

import com.pwaquatics.shop.cart.application.CartOwner;
import com.pwaquatics.shop.cart.application.CartResult;
import com.pwaquatics.shop.cart.application.CartService;
import com.pwaquatics.shop.cart.application.view.CartView;
import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.security.ShopUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cart for guests and customers (docs/API.md §2.4). Guests are recognised by the
 * {@code CART_TOKEN} cookie, customers by their session. Every call returns the full,
 * freshly calculated cart.
 */
@RestController
@RequestMapping("/api/v1/cart")
@Tag(name = "Cart")
class CartController {

    static final String COOKIE = "CART_TOKEN";
    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final CartService cartService;
    private final AppProperties properties;

    CartController(CartService cartService, AppProperties properties) {
        this.cartService = cartService;
        this.properties = properties;
    }

    @Operation(summary = "The current cart (empty if there is none)")
    @GetMapping
    CartView cart(
            @AuthenticationPrincipal ShopUserPrincipal principal,
            @CookieValue(name = COOKIE, required = false) String token,
            HttpServletResponse response) {
        CartOwner owner = owner(principal, token);
        return respond(cartService.view(owner), owner, false, response);
    }

    @Operation(summary = "Add a variant to the cart")
    @PostMapping("/items")
    CartView add(
            @AuthenticationPrincipal ShopUserPrincipal principal,
            @CookieValue(name = COOKIE, required = false) String token,
            @Valid @RequestBody AddCartItemRequest request,
            HttpServletRequest http,
            HttpServletResponse response) {
        CartOwner owner = owner(principal, token);
        return respond(
                cartService.add(owner, request.variantId(), request.quantity(), http.getRemoteAddr()),
                owner, true, response);
    }

    @Operation(summary = "Change the quantity of a cart line")
    @PatchMapping("/items/{itemId}")
    CartView changeQuantity(
            @AuthenticationPrincipal ShopUserPrincipal principal,
            @CookieValue(name = COOKIE, required = false) String token,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateCartItemRequest request,
            HttpServletRequest http,
            HttpServletResponse response) {
        CartOwner owner = owner(principal, token);
        return respond(
                cartService.changeQuantity(owner, itemId, request.quantity(), http.getRemoteAddr()),
                owner, true, response);
    }

    @Operation(summary = "Remove a cart line")
    @DeleteMapping("/items/{itemId}")
    CartView remove(
            @AuthenticationPrincipal ShopUserPrincipal principal,
            @CookieValue(name = COOKIE, required = false) String token,
            @PathVariable UUID itemId,
            HttpServletRequest http,
            HttpServletResponse response) {
        CartOwner owner = owner(principal, token);
        return respond(cartService.remove(owner, itemId, http.getRemoteAddr()), owner, true, response);
    }

    @Operation(summary = "Empty the cart")
    @DeleteMapping
    CartView clear(
            @AuthenticationPrincipal ShopUserPrincipal principal,
            @CookieValue(name = COOKIE, required = false) String token,
            HttpServletRequest http,
            HttpServletResponse response) {
        CartOwner owner = owner(principal, token);
        return respond(cartService.clear(owner, http.getRemoteAddr()), owner, true, response);
    }

    private static CartOwner owner(ShopUserPrincipal principal, String token) {
        boolean wellFormed = token != null && TOKEN_FORMAT.matcher(token).matches();
        return new CartOwner(principal == null ? null : principal.userId(), wellFormed ? token : null);
    }

    /** Applies the cookie changes the service asked for and returns the cart. */
    private CartView respond(CartResult result, CartOwner owner, boolean changed, HttpServletResponse response) {
        if (result.issuedGuestToken() != null) {
            setCookie(response, result.issuedGuestToken(), CartService.GUEST_CART_LIFETIME);
        } else if (result.guestTokenConsumed()) {
            setCookie(response, "", Duration.ZERO);
        } else if (changed && !owner.isCustomer() && owner.hasGuestToken()) {
            // Keep the cookie alive as long as the guest cart is in use.
            setCookie(response, owner.guestToken(), CartService.GUEST_CART_LIFETIME);
        }
        return result.cart();
    }

    private void setCookie(HttpServletResponse response, String value, Duration maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(properties.cookies().secure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build()
                .toString());
    }
}
