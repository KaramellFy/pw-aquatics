package com.pwaquatics.shop.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeEmailRequest(
        @NotBlank @Email @Size(max = 254) String newEmail, @NotBlank @Size(max = 128) String currentPassword) {}
