package com.pwaquatics.shop.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Application settings ({@code app.*}). Secrets come from environment variables only. */
@Validated
@ConfigurationProperties("app")
public record AppProperties(
        @NotBlank String baseUrl,
        @Valid @NotNull Cookies cookies,
        @Valid @NotNull Mail mail,
        @Valid @NotNull Outbox outbox,
        @Valid @NotNull Media media,
        @Valid @NotNull Security security,
        AdminBootstrap adminBootstrap,
        Map<String, @Valid RateLimit> rateLimits) {

    /** Cookie flags; {@code secure} must be true in production (HTTPS). */
    public record Cookies(boolean secure) {}

    /** Sender address and the shop inbox that receives contact messages. */
    public record Mail(@NotBlank String from, @NotBlank String shopInbox) {}

    /** Transactional outbox worker settings. */
    public record Outbox(
            boolean schedulerEnabled, @NotNull Duration pollInterval, @Min(1) int batchSize, @Min(1) int maxAttempts) {}

    /** Media storage: local directory served under {@code publicPath}. */
    public record Media(@NotBlank String localPath, @NotBlank String publicPath) {}

    /** Login protection and lifetimes of emailed one-time links. */
    public record Security(
            @Min(1) int maxFailedLogins,
            @NotNull Duration lockDuration,
            @NotNull Duration verificationTokenTtl,
            @NotNull Duration passwordResetTokenTtl) {}

    /** Optional first administrator, created at startup when no admin exists yet. */
    public record AdminBootstrap(String email, String password) {

        public boolean isConfigured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }
    }

    /** A token bucket: {@code capacity} requests per {@code period}. */
    public record RateLimit(@Min(1) int capacity, @NotNull Duration period) {}

    public RateLimit rateLimit(String name) {
        RateLimit limit = rateLimits == null ? null : rateLimits.get(name);
        if (limit == null) {
            throw new IllegalStateException("No rate limit configured for '" + name + "'");
        }
        return limit;
    }
}
