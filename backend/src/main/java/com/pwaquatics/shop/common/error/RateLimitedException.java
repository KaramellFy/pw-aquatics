package com.pwaquatics.shop.common.error;

import java.time.Duration;

/** Too many requests from one client. Maps to 429 with a {@code Retry-After} header. */
public class RateLimitedException extends ShopException {

    private final Duration retryAfter;

    public RateLimitedException(Duration retryAfter) {
        super("RATE_LIMITED", "Too many requests. Please try again later.");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
