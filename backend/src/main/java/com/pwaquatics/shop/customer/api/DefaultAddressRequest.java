package com.pwaquatics.shop.customer.api;

import jakarta.validation.constraints.AssertTrue;

/** Which default(s) the address should become. At least one must be true. */
public record DefaultAddressRequest(boolean shipping, boolean billing) {

    @AssertTrue(message = "Choose shipping, billing or both")
    boolean isAnySelected() {
        return shipping || billing;
    }
}
