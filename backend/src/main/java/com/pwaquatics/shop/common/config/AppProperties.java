package com.pwaquatics.shop.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
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
        @Valid @NotNull Shipping shipping,
        @Valid @NotNull Orders orders,
        @Valid @NotNull BankTransfer bankTransfer,
        @Valid @NotNull Stripe stripe,
        @Valid @NotNull Retention retention,
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

    /** Days needed before a parcel can leave (0 = orders can ship the same day). */
    public record Shipping(@Min(0) @Max(30) int handlingDays) {}

    /** How long a bank-transfer order waits for its payment before it is cancelled and its stock returned. */
    /**
     * Payment deadlines of unpaid orders; when they pass, the order is cancelled and its
     * animals go back on sale.
     *
     * @param bankTransferPaymentDays days (until the end of the day) to transfer the money
     * @param cardPaymentMinutes minutes to complete an online payment that was started
     */
    public record Orders(
            @Min(1) @Max(60) int bankTransferPaymentDays, @Min(15) @Max(1440) int cardPaymentMinutes) {}

    /**
     * The shop's bank account, shown to customers who pay by bank transfer. Without an
     * IBAN the payment method is not offered.
     */
    public record BankTransfer(String accountHolder, String iban, String bic, String bankName) {

        public boolean isConfigured() {
            return iban != null && !iban.isBlank() && accountHolder != null && !accountHolder.isBlank();
        }
    }

    /**
     * Stripe account for online payments (cards, Apple Pay, Google Pay). Offered only when
     * all three values are set; the publishable key is the only one sent to browsers.
     *
     * @param webhookSecret signing secret of the webhook endpoint ({@code whsec_…})
     */
    public record Stripe(String secretKey, String publishableKey, String webhookSecret) {

        public boolean isConfigured() {
            return !blank(secretKey) && !blank(publishableKey) && !blank(webhookSecret);
        }

        /** All three or none: a half-configured account would take payments it never hears back about. */
        @AssertTrue(message = "STRIPE_SECRET_KEY, STRIPE_PUBLISHABLE_KEY and STRIPE_WEBHOOK_SECRET must be set together")
        public boolean isCompleteOrEmpty() {
            return isConfigured() || (blank(secretKey) && blank(publishableKey) && blank(webhookSecret));
        }

        /** Test keys and live keys must not be mixed. */
        @AssertTrue(message = "Stripe keys must both be test keys or both be live keys")
        public boolean isOneMode() {
            if (!isConfigured()) {
                return true;
            }
            boolean testSecret = secretKey.startsWith("sk_test_") || secretKey.startsWith("rk_test_");
            return testSecret == publishableKey.startsWith("pk_test_");
        }

        private static boolean blank(String value) {
            return value == null || value.isBlank();
        }
    }

    /**
     * How long personal data is kept where no law requires longer (docs/DATABASE.md §6).
     *
     * @param sentEmails delivered emails: the text is erased right after sending; recipient and
     *     subject are deleted after this period
     * @param failedEmails emails that could not be delivered (kept for investigation)
     * @param auditIpAddresses IP addresses in the audit log are erased after this period; the
     *     entries themselves stay
     * @param contactMessages contact form messages are deleted after this period
     * @param paymentWebhookEvents processed payment webhook deliveries (no personal data; kept
     *     to recognise repeated deliveries and to trace payment problems)
     */
    public record Retention(
            @NotNull Duration sentEmails,
            @NotNull Duration failedEmails,
            @NotNull Duration auditIpAddresses,
            @NotNull Duration contactMessages,
            @NotNull Duration paymentWebhookEvents) {}

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
