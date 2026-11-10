package com.pwaquatics.shop.identity.domain;

import java.util.UUID;

/**
 * Published (inside the deleting transaction) when an account is anonymised, so other
 * modules can remove the personal data they hold for that user.
 */
public record UserAnonymised(UUID userId) {}
