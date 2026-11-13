package com.pwaquatics.shop.payment.application;

import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.common.error.BadRequestException;
import com.pwaquatics.shop.common.error.NotFoundException;
import com.pwaquatics.shop.payment.infrastructure.StripeSignatureVerifier;
import com.pwaquatics.shop.payment.infrastructure.WebhookEventLog;
import com.pwaquatics.shop.security.ratelimit.RateLimiter;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Receives Stripe's webhook deliveries ({@code POST /api/v1/webhooks/stripe}).
 *
 * <ol>
 *   <li>The signature is checked first; anything not signed by Stripe is refused.
 *   <li>The event is stored once per Stripe event id, reduced to ids, amounts and statuses
 *       (Stripe's events can contain names, addresses and the last digits of cards, which we
 *       never keep).
 *   <li>It is processed in one transaction with its effect on the order. If that fails, the
 *       error is noted, the response is an error and Stripe delivers the event again later.
 * </ol>
 */
@Service
public class StripeWebhooks {

    static final String PROVIDER = "STRIPE";

    private static final List<String> TEXT_FIELDS = List.of("object", "id", "status", "currency", "payment_intent");
    private static final List<String> NUMBER_FIELDS = List.of("amount", "amount_received", "amount_refunded");

    private final RateLimiter rateLimiter;
    private final StripeSignatureVerifier signatures;
    private final WebhookEventLog events;
    private final StripeEventProcessor processor;
    private final AppProperties.Stripe account;
    private final ObjectMapper json;
    private final Clock clock;

    StripeWebhooks(
            RateLimiter rateLimiter,
            StripeSignatureVerifier signatures,
            WebhookEventLog events,
            StripeEventProcessor processor,
            AppProperties properties,
            ObjectMapper json,
            Clock clock) {
        this.rateLimiter = rateLimiter;
        this.signatures = signatures;
        this.events = events;
        this.processor = processor;
        this.account = properties.stripe();
        this.json = json;
        this.clock = clock;
    }

    /**
     * @param payload the request body exactly as received (the signature covers its bytes)
     * @throws NotFoundException if Stripe is not configured
     * @throws BadRequestException {@code WEBHOOK_SIGNATURE_INVALID} or {@code WEBHOOK_INVALID}
     */
    public void receive(String payload, String signatureHeader, String clientIp) {
        if (!account.isConfigured()) {
            throw new NotFoundException("Not found.");
        }
        rateLimiter.consume("payment-webhook", clientIp);
        if (!signatures.isValid(payload, signatureHeader)) {
            throw new BadRequestException("WEBHOOK_SIGNATURE_INVALID", "The webhook signature is not valid.");
        }
        JsonNode event = parse(payload);
        String eventId = event.path("id").asString("");
        String type = event.path("type").asString("");
        JsonNode object = event.path("data").path("object");
        if (!eventId.startsWith("evt_") || type.isBlank() || !object.isObject()) {
            throw new BadRequestException("WEBHOOK_INVALID", "The webhook event is incomplete.");
        }

        UUID row = events.store(PROVIDER, eventId, type, reduced(event, object).toString(), clock.instant());
        try {
            processor.process(row, type, object);
        } catch (RuntimeException e) {
            events.recordError(row, e.getClass().getSimpleName() + ": " + abbreviate(e.getMessage()));
            throw e;
        }
    }

    private JsonNode parse(String payload) {
        try {
            return json.readTree(payload);
        } catch (JacksonException e) {
            throw new BadRequestException("WEBHOOK_INVALID", "The webhook body is not valid JSON.");
        }
    }

    /** Only what is needed to follow a payment: no names, addresses, emails or card details. */
    private ObjectNode reduced(JsonNode event, JsonNode object) {
        ObjectNode kept = json.createObjectNode();
        kept.put("id", event.path("id").asString());
        kept.put("type", event.path("type").asString());
        kept.put("created", event.path("created").asLong());
        kept.put("livemode", event.path("livemode").asBoolean());
        ObjectNode keptObject = kept.putObject("object");
        TEXT_FIELDS.forEach(field -> {
            if (object.path(field).isString()) {
                keptObject.put(field, object.path(field).asString());
            }
        });
        NUMBER_FIELDS.forEach(field -> {
            if (object.path(field).isNumber()) {
                keptObject.put(field, object.path(field).asLong());
            }
        });
        String failure = StripeEventProcessor.failureCode(object);
        if (failure != null) {
            keptObject.put("failure_code", failure);
        }
        return kept;
    }

    private static String abbreviate(String message) {
        if (message == null) {
            return "";
        }
        return message.length() <= 300 ? message : message.substring(0, 300);
    }
}
