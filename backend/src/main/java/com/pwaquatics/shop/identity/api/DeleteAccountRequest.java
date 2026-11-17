package com.pwaquatics.shop.identity.api;

import jakarta.validation.constraints.Size;

public record DeleteAccountRequest(@Size(max = 128) String currentPassword) {}
