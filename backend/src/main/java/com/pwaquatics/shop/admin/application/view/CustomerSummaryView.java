package com.pwaquatics.shop.admin.application.view;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.identity.domain.UserStatus;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.UUID;

/**
 * A customer in the admin list.
 *
 * @param orderCount every order placed, including cancelled ones
 * @param lifetimeValue paid orders that count as sales (not cancelled, not refunded), gross
 */
public record CustomerSummaryView(
        UUID id,
        String name,
        String email,
        UserStatus status,
        boolean emailVerified,
        Instant registeredAt,
        @Nullable Instant lastLoginAt,
        int orderCount,
        Money lifetimeValue,
        @Nullable Instant lastOrderAt) {}
