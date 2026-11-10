package com.pwaquatics.shop.identity.application;

/** Command for {@link AccountService#updateProfile}. {@code locale} may be null (unchanged). */
public record UpdateProfile(String firstName, String lastName, String phone, boolean marketingOptIn, String locale) {}
