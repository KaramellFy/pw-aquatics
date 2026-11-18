package com.pwaquatics.shop.identity.application.view;

import jakarta.annotation.Nullable;
import java.time.Instant;

/**
 * Two-step sign-in of the signed-in administrator.
 *
 * @param enabled codes from an authenticator app are asked for at sign-in
 * @param enabledAt when the current app was set up
 * @param recoveryCodesLeft unused recovery codes
 */
public record TwoFactorStatusView(boolean enabled, @Nullable Instant enabledAt, int recoveryCodesLeft) {}
