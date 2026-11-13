package com.pwaquatics.shop.payment.api;

import com.pwaquatics.shop.common.error.BadRequestException;
import com.pwaquatics.shop.payment.application.StripeWebhooks;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stripe's webhook endpoint (docs/API.md §2.7). Called by Stripe, not by the shop's pages:
 * no session and no CSRF token; the {@code Stripe-Signature} header proves where it came from.
 */
@RestController
@Hidden
class StripeWebhookController {

    /** Stripe's events are a few kilobytes; anything far larger is not from Stripe. */
    static final int MAX_PAYLOAD_BYTES = 256 * 1024;

    private final StripeWebhooks webhooks;

    StripeWebhookController(StripeWebhooks webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping("/api/v1/webhooks/stripe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void receive(@RequestHeader(name = "Stripe-Signature", required = false) String signature, HttpServletRequest request)
            throws IOException {
        webhooks.receive(readLimited(request), signature, request.getRemoteAddr());
    }

    /** The signature covers the exact bytes, so the body is read as is (not parsed and re-serialised). */
    private static String readLimited(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > MAX_PAYLOAD_BYTES) {
            throw tooLarge();
        }
        try (InputStream in = request.getInputStream()) {
            byte[] body = in.readNBytes(MAX_PAYLOAD_BYTES + 1);
            if (body.length > MAX_PAYLOAD_BYTES) {
                throw tooLarge();
            }
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private static BadRequestException tooLarge() {
        return new BadRequestException("WEBHOOK_INVALID", "The webhook body is too large.");
    }
}
