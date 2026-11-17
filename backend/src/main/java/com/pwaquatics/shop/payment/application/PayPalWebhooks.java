package com.pwaquatics.shop.payment.application;

import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.common.error.BadRequestException;
import com.pwaquatics.shop.common.error.NotFoundException;
import com.pwaquatics.shop.payment.application.PayPalGateway.WebhookSignature;
import com.pwaquatics.shop.payment.infrastructure.WebhookEventLog;
import com.pwaquatics.shop.security.ratelimit.RateLimiter;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.node.ObjectNode;

/**
 * Receives PayPal's webhook deliveries ({@code POST /api/v1/webhooks/paypal}).
 *
 * <ol>
 *   <li>PayPal confirms that the delivery is genuine (its verify-webhook-signature API, for our
 *       webhook id); anything else is refused.
 *   <li>The event is stored once per PayPal event id, reduced to ids, amounts and statuses
 *       (PayPal's events can contain the payer's name and email address, which we never keep).
 *   <li>It is processed in one transaction with its effect on the order. If that fails, the
 *       error is noted, the response is an error and PayPal delivers the event again later.
 * </ol>
 */
@Service
public class PayPalWebhooks {

    static final String PROVIDER = "PAYPAL";

    private final RateLimiter rateLimiter;
    private final PayPalGateway paypal;
    private final WebhookEventLog events;
    private final PayPalEventProcessor processor;
    private final AppProperties.PayPal account;
    private final ObjectMapper json;
    private final ObjectReader strictReader;
    private final Clock clock;

    PayPalWebhooks(
            RateLimiter rateLimiter,
            PayPalGateway paypal,
            WebhookEventLog events,
            PayPalEventProcessor processor,
            AppProperties properties,
            ObjectMapper json,
            Clock clock) {
        this.rateLimiter = rateLimiter;
        this.paypal = paypal;
        this.events = events;
        this.processor = processor;
        this.account = properties.paypal();
        this.json = json;
        // The body goes to PayPal as it is, so it must be exactly one JSON object.
        this.strictReader = json.readerFor(JsonNode.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.clock = clock;
    }

    /**
     * @param payload the request body exactly as received (PayPal checks it byte for byte)
     * @throws NotFoundException if PayPal is not configured
     * @throws BadRequestException {@code WEBHOOK_SIGNATURE_INVALID} or {@code WEBHOOK_INVALID}
     */
    public void receive(String payload, WebhookSignature signature, String clientIp) {
        if (!account.isConfigured()) {
            throw new NotFoundException("Not found.");
        }
        rateLimiter.consume("payment-webhook", clientIp);
        JsonNode event = parse(payload);
        if (!signature.isComplete() || !paypal.isGenuine(signature, payload)) {
            throw new BadRequestException("WEBHOOK_SIGNATURE_INVALID", "The webhook signature is not valid.");
        }
        String eventId = event.path("id").asString("");
        String type = event.path("event_type").asString("");
        JsonNode resource = event.path("resource");
        if (!eventId.startsWith("WH-") || eventId.length() > 100 || type.isBlank() || !resource.isObject()) {
            throw new BadRequestException("WEBHOOK_INVALID", "The webhook event is incomplete.");
        }

        UUID row = events.store(PROVIDER, eventId, type, reduced(event, resource).toString(), clock.instant());
        try {
            processor.process(row, type, resource);
        } catch (RuntimeException e) {
            events.recordError(row, e.getClass().getSimpleName() + ": " + abbreviate(e.getMessage()));
            throw e;
        }
    }

    private JsonNode parse(String payload) {
        try {
            JsonNode event = strictReader.readValue(payload);
            if (event == null || !event.isObject()) {
                throw new BadRequestException("WEBHOOK_INVALID", "The webhook body is not an event.");
            }
            return event;
        } catch (JacksonException e) {
            throw new BadRequestException("WEBHOOK_INVALID", "The webhook body is not valid JSON.");
        }
    }

    /** Only what is needed to follow a payment: no names, email addresses or account details. */
    private ObjectNode reduced(JsonNode event, JsonNode resource) {
        ObjectNode kept = json.createObjectNode();
        kept.put("id", event.path("id").asString());
        kept.put("event_type", event.path("event_type").asString());
        kept.put("create_time", event.path("create_time").asString(""));
        kept.put("resource_type", event.path("resource_type").asString(""));
        ObjectNode keptResource = kept.putObject("resource");
        keptResource.put("id", resource.path("id").asString(""));
        keptResource.put("status", resource.path("status").asString(""));
        JsonNode amount = resource.path("amount");
        if (amount.isObject()) {
            keptResource.putObject("amount")
                    .put("value", amount.path("value").asString(""))
                    .put("currency_code", amount.path("currency_code").asString(""));
        }
        String order = resource.path("supplementary_data").path("related_ids").path("order_id").asString("");
        if (!order.isBlank()) {
            keptResource.put("order_id", order);
        }
        String capture = PayPalEventProcessor.captureIdOf(resource);
        if (capture != null) {
            keptResource.put("capture_id", capture);
        }
        JsonNode refunded = resource.path("seller_payable_breakdown").path("total_refunded_amount");
        if (refunded.isObject()) {
            keptResource.putObject("total_refunded_amount")
                    .put("value", refunded.path("value").asString(""))
                    .put("currency_code", refunded.path("currency_code").asString(""));
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
