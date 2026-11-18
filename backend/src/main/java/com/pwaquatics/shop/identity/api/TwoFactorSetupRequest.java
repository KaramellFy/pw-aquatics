package com.pwaquatics.shop.identity.api;

import jakarta.validation.constraints.Size;

/** @param code a code from the app in use (or a recovery code); needed only to replace a working app */
public record TwoFactorSetupRequest(@Size(max = 32) String code) {}
