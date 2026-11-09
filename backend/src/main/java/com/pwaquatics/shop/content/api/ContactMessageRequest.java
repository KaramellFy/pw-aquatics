package com.pwaquatics.shop.content.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/contact} (docs/API.md §2.3). */
public record ContactMessageRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(min = 20, max = 5000) String message,
        @Size(max = 20) @Pattern(regexp = "^[A-Za-z0-9-]*$") String orderNumber,
        /* Honeypot field; must stay empty for humans. */
        @Size(max = 500) String website) {}
