package com.pwaquatics.shop.identity.application;

/** Command for {@link RegistrationService#register}. {@code locale} is "en" or "de". */
public record RegisterAccount(
        String email, String password, String firstName, String lastName, boolean marketingOptIn, String locale) {}
