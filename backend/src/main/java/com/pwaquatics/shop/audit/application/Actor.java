package com.pwaquatics.shop.audit.application;

import java.util.UUID;

/** Who performs an action (for the audit log): the signed-in user and their IP address. */
public record Actor(UUID userId, String ip) {}
