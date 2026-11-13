package com.pwaquatics.shop.admin.application.view;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.identity.domain.UserStatus;
import com.pwaquatics.shop.order.domain.OrderStatus;
import com.pwaquatics.shop.order.domain.PaymentStatus;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One customer for the admin area: profile, address book, orders and what they have spent.
 *
 * @param lockable whether the shop can lock (or unlock) this account
 * @param lifetimeValue paid orders that count as sales (not cancelled, not refunded), gross
 * @param averageOrderValue {@code lifetimeValue} divided by the paid orders, rounded; null without any
 * @param orders the most recent orders (up to 50), newest first
 */
public record CustomerDetailView(
        UUID id,
        String firstName,
        String lastName,
        String email,
        @Nullable String phone,
        String locale,
        UserStatus status,
        boolean lockable,
        boolean emailVerified,
        boolean marketingOptIn,
        Instant registeredAt,
        @Nullable Instant lastLoginAt,
        int orderCount,
        int paidOrderCount,
        Money lifetimeValue,
        @Nullable Money averageOrderValue,
        List<CustomerAddress> addresses,
        List<CustomerOrder> orders) {

    public record CustomerAddress(
            UUID id,
            @Nullable String label,
            String firstName,
            String lastName,
            @Nullable String company,
            String street,
            String houseNumber,
            @Nullable String addressLine2,
            String postalCode,
            String city,
            String countryCode,
            @Nullable String phone,
            boolean defaultShipping,
            boolean defaultBilling) {}

    public record CustomerOrder(
            String orderNumber,
            Instant placedAt,
            OrderStatus status,
            PaymentStatus paymentStatus,
            Money total,
            int itemCount) {}
}
