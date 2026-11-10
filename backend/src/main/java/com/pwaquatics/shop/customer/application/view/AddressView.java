package com.pwaquatics.shop.customer.application.view;

import jakarta.annotation.Nullable;
import java.util.UUID;

public record AddressView(
        UUID id,
        @Nullable String label,
        String firstName,
        String lastName,
        @Nullable String company,
        String street,
        String houseNumber,
        @Nullable String addressLine2,
        String postalCode,
        String city,
        String countryCode,
        @Nullable String phone,
        boolean defaultShipping,
        boolean defaultBilling) {}
