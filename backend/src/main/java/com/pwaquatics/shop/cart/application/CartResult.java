package com.pwaquatics.shop.cart.application;

import com.pwaquatics.shop.cart.application.view.CartView;

/**
 * A cart plus what the web layer must do with the guest cookie.
 *
 * @param issuedGuestToken a new token to store in the cookie (a guest cart was just created), or null
 * @param guestTokenConsumed the guest cookie should be deleted (merged into the customer cart, or stale)
 */
public record CartResult(CartView cart, String issuedGuestToken, boolean guestTokenConsumed) {}
