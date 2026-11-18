package com.pwaquatics.shop.identity.application.view;

/**
 * A new key for the authenticator app, shown once during setup.
 *
 * @param secret the key in Base32, for typing it in by hand
 * @param otpauthUri the same as an {@code otpauth://} link, shown as a QR code
 */
public record TwoFactorSetupView(String secret, String otpauthUri) {}
