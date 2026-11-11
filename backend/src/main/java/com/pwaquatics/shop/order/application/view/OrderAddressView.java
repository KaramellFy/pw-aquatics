package com.pwaquatics.shop.order.application.view;

import jakarta.annotation.Nullable;

/** An address as it was stored with the order. */
public record OrderAddressView(
        String firstName,
        String lastName,
        @Nullable String company,
        String street,
        String houseNumber,
        @Nullable String addressLine2,
        String postalCode,
        String city,
        String countryCode,
        @Nullable String phone) {}
