package com.pwaquatics.shop.identity.api;

/** Ways to sign in besides email and password that this shop offers. */
public record SignInOptionsView(boolean google) {}
