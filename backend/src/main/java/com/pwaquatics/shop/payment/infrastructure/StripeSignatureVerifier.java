package com.pwaquatics.shop.payment.infrastructure;

import com.pwaquatics.shop.common.config.AppProperties;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * Checks the {@code Stripe-Signature} header of a webhook delivery: an HMAC of the exact
 * request body with the endpoint's signing secret, with a timestamp no older than five
 * minutes (against replayed deliveries).
 */
@Component
public class StripeSignatureVerifier {

    private static final long TOLERANCE_SECONDS = 300;

    private final String secret;
    private final Clock clock;

    StripeSignatureVerifier(AppProperties properties, Clock clock) {
        this.secret = properties.stripe().webhookSecret();
        this.clock = clock;
    }

    /** @return whether the body was signed by Stripe with our secret, recently */
    public boolean isValid(String payload, String signatureHeader) {
        if (secret == null || secret.isBlank() || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        try {
            return Webhook.Signature.verifyHeader(payload, signatureHeader, secret, TOLERANCE_SECONDS, clock);
        } catch (SignatureVerificationException e) {
            return false;
        }
    }
}
