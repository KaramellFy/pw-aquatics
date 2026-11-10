package com.pwaquatics.shop.customer.domain;

/** The editable fields of a postal address. Optional values may be null. */
public record AddressData(
        String label,
        String firstName,
        String lastName,
        String company,
        String street,
        String houseNumber,
        String addressLine2,
        String postalCode,
        String city,
        String countryCode,
        String phone) {}
