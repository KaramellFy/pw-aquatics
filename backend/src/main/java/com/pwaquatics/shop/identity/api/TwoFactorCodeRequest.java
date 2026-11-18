package com.pwaquatics.shop.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A code from the authenticator app (six digits) or a recovery code. */
public record TwoFactorCodeRequest(@NotBlank @Size(max = 32) String code) {}
