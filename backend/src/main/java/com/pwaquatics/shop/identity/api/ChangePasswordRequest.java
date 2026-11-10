package com.pwaquatics.shop.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 128) String currentPassword, @NotBlank @Size(min = 10, max = 128) String newPassword) {}
