package com.pwaquatics.shop.cart.application;

import java.util.UUID;

/**
 * Who a cart request comes from: a signed-in customer ({@code userId}), a guest browser
 * ({@code guestToken} from the cart cookie), both (just signed in — the guest cart is merged)
 * or neither (no cart yet).
 */
public record CartOwner(UUID userId, String guestToken) {

    public boolean isCustomer() {
        return userId != null;
    }

    public boolean hasGuestToken() {
        return guestToken != null && !guestToken.isBlank();
    }
}
