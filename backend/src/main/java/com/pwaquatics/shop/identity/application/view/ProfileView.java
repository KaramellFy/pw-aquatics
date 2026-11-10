package com.pwaquatics.shop.identity.application.view;

import jakarta.annotation.Nullable;
import java.time.Instant;

public record ProfileView(
        String email,
        String firstName,
        String lastName,
        @Nullable String phone,
        boolean marketingOptIn,
        String locale,
        Instant memberSince) {}
