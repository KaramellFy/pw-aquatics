package com.pwaquatics.shop.content.application.view;

import com.pwaquatics.shop.content.domain.ContactMessageStatus;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.UUID;

/** A message from the contact form, as shown in the admin inbox. */
public record ContactMessageView(
        UUID id,
        String name,
        String email,
        String subject,
        String message,
        @Nullable String orderNumber,
        ContactMessageStatus status,
        Instant createdAt) {}
