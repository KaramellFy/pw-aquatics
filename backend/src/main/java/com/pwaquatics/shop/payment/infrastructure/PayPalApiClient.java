package com.pwaquatics.shop.payment.infrastructure;

import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.common.error.ServiceUnavailableException;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.payment.application.PayPalAmounts;
import com.pwaquatics.shop.payment.application.PayPalGateway;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@link PayPalGateway} on PayPal's REST API (OAuth 2 client credentials, Orders v2, webhook
 * verification), called with the JDK's HTTP client. Only ids, amounts, the order number and the
 * page language go to PayPal from here; the customer signs in and chooses how to pay in PayPal's
 * own window. No address is sent and none is asked for ({@code NO_SHIPPING}): the shop has it.
 */
@Component
class PayPalApiClient implements PayPalGateway {

    private static final Logger log = LoggerFactory.getLogger(PayPalApiClient.class);
    /** PayPal order ids are short upper-case codes; anything else is never put into a URL. */
    private static final Pattern PAYPAL_ID = Pattern.compile("[A-Z0-9]{1,50}");
    /** PayPal's reasons for not taking the money where the customer may approve again. */
    private static final Set<String> REFUSED = Set.of("INSTRUMENT_DECLINED", "ORDER_NOT_APPROVED", "PAYER_ACTION_REQUIRED");
    private static final Duration TOKEN_MARGIN = Duration.ofMinutes(1);

    private final AppProperties.PayPal account;
    private final URI base;
    private final ObjectMapper json;
    private final Clock clock;
    private final HttpClient http;

    private volatile AccessToken token;

    @Autowired
    PayPalApiClient(AppProperties properties, ObjectMapper json, Clock clock) {
        this(properties.paypal(), properties.paypal().apiBase(), json, clock);
    }

    /** @param base PayPal's API, or a stand-in for tests */
    PayPalApiClient(AppProperties.PayPal account, URI base, ObjectMapper json, Clock clock) {
        this.account = account;
        this.base = base;
        this.json = json;
        this.clock = clock;
        this.http = HttpClient.newBuilder()
                .connectTimeout(account.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public PayPalOrder createOrder(UUID orderId, String orderNumber, Money amount, String language) {
        ObjectNode body = json.createObjectNode();
        body.put("intent", "CAPTURE");
        ObjectNode unit = body.putArray("purchase_units").addObject();
        unit.put("reference_id", orderNumber);
        unit.put("custom_id", orderNumber);
        unit.put("description", "PW Aquatics " + orderNumber);
        unit.putObject("amount")
                .put("currency_code", amount.currency())
                .put("value", PayPalAmounts.value(amount));
        body.putObject("payment_source").putObject("paypal").putObject("experience_context")
                .put("brand_name", "PW Aquatics")
                .put("locale", "de".equals(language) ? "de-DE" : "en-GB")
                // The shop has the delivery address; PayPal neither asks for one nor sends one back.
                .put("shipping_preference", "NO_SHIPPING")
                .put("user_action", "PAY_NOW");
        HttpRequest.Builder request = request("/v2/checkout/orders")
                // The same id for the same order: a repeated call returns the first PayPal order.
                .header("PayPal-Request-Id", "order-" + orderId)
                .header("Prefer", "return=representation")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        return order(read(send(request, "create order")));
    }

    @Override
    public PayPalOrder getOrder(String paypalOrderId) {
        return order(read(send(request("/v2/checkout/orders/" + checked(paypalOrderId)).GET(), "get order")));
    }

    @Override
    public PayPalOrder capture(String paypalOrderId, UUID orderId) {
        HttpRequest.Builder request = request("/v2/checkout/orders/" + checked(paypalOrderId) + "/capture")
                .header("PayPal-Request-Id", "order-" + orderId + "-capture")
                .header("Prefer", "return=representation")
                .POST(HttpRequest.BodyPublishers.ofString("{}"));
        HttpResponse<String> response = exchange(request, "capture");
        if (response.statusCode() == 422) {
            List<String> issues = issues(response.body());
            if (issues.contains("ORDER_ALREADY_CAPTURED")) {
                return getOrder(paypalOrderId);
            }
            for (String issue : issues) {
                if (REFUSED.contains(issue)) {
                    throw new CaptureRefused(issue);
                }
            }
        }
        return order(read(checkedResponse(response, "capture")));
    }

    @Override
    public boolean isGenuine(WebhookSignature signature, String payload) {
        ObjectNode fields = json.createObjectNode()
                .put("auth_algo", signature.authAlgo())
                .put("cert_url", signature.certUrl())
                .put("transmission_id", signature.transmissionId())
                .put("transmission_sig", signature.transmissionSig())
                .put("transmission_time", signature.transmissionTime())
                .put("webhook_id", account.webhookId());
        // PayPal compares the event byte for byte, so it is passed on exactly as received
        // (the caller has made sure it is a single JSON object).
        String body = fields.toString();
        body = body.substring(0, body.length() - 1) + ",\"webhook_event\":" + payload + "}";
        HttpResponse<String> response = exchange(
                request("/v1/notifications/verify-webhook-signature").POST(HttpRequest.BodyPublishers.ofString(body)),
                "verify webhook");
        if (response.statusCode() >= 400 && response.statusCode() < 500) {
            log.warn("PayPal refused to verify a webhook delivery: {} {}", response.statusCode(), issues(response.body()));
            return false;
        }
        return "SUCCESS".equals(read(checkedResponse(response, "verify webhook")).path("verification_status").asString(""));
    }

    @Override
    public Refund refund(String captureId, Money amount, String idempotencyKey) {
        ObjectNode body = json.createObjectNode();
        body.putObject("amount")
                .put("value", PayPalAmounts.value(amount))
                .put("currency_code", amount.currency());
        HttpRequest.Builder request = request("/v2/payments/captures/" + checked(captureId) + "/refund")
                .header("PayPal-Request-Id", "refund-" + idempotencyKey)
                .header("Prefer", "return=representation")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        HttpResponse<String> response = exchange(request, "refund");
        if (response.statusCode() == 422) {
            List<String> issues = issues(response.body());
            log.warn("PayPal refused a refund: {} (debug id {})", issues, debugId(response.body()));
            throw new RefundRefused(issues.isEmpty() ? "UNPROCESSABLE_ENTITY" : issues.getFirst());
        }
        JsonNode refund = read(checkedResponse(response, "refund"));
        String id = refund.path("id").asString("");
        if (id.isBlank()) {
            log.warn("PayPal sent a refund without id");
            throw unavailable(null);
        }
        Money refunded = refund.path("amount").isObject() ? money(refund.path("amount")) : amount;
        return new Refund(id, refund.path("status").asString(""), refunded);
    }

    // ------------------------------------------------------------------ HTTP

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(base.resolve(path))
                .timeout(account.timeout())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
    }

    private HttpResponse<String> send(HttpRequest.Builder request, String what) {
        return checkedResponse(exchange(request, what), what);
    }

    /** Sends with a valid access token; a token PayPal no longer accepts is renewed once. */
    private HttpResponse<String> exchange(HttpRequest.Builder request, String what) {
        HttpResponse<String> response = call(request.copy().header("Authorization", "Bearer " + accessToken()).build(), what);
        if (response.statusCode() == 401) {
            token = null;
            response = call(request.copy().header("Authorization", "Bearer " + accessToken()).build(), what);
        }
        return response;
    }

    private HttpResponse<String> checkedResponse(HttpResponse<String> response, String what) {
        if (response.statusCode() / 100 != 2) {
            // PayPal's error name, issue codes and debug id help with support; no customer data is involved.
            log.warn("PayPal {} failed: {} {} (debug id {})", what, response.statusCode(), issues(response.body()),
                    debugId(response.body()));
            throw unavailable(null);
        }
        return response;
    }

    private HttpResponse<String> call(HttpRequest request, String what) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("PayPal {} could not be reached: {}", what, e.toString());
            throw unavailable(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable(e);
        }
    }

    private String accessToken() {
        AccessToken known = token;
        if (known != null && clock.instant().isBefore(known.expiresAt())) {
            return known.value();
        }
        synchronized (this) {
            known = token;
            if (known != null && clock.instant().isBefore(known.expiresAt())) {
                return known.value();
            }
            if (!account.isConfigured()) {
                throw new IllegalStateException("PayPal is not configured");
            }
            String credentials = Base64.getEncoder().encodeToString(
                    (account.clientId() + ":" + account.clientSecret()).getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(base.resolve("/v1/oauth2/token"))
                    .timeout(account.timeout())
                    .header("Authorization", "Basic " + credentials)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                    .build();
            JsonNode answer = read(checkedResponse(call(request, "sign-in"), "sign-in"));
            String value = answer.path("access_token").asString("");
            if (value.isBlank()) {
                throw unavailable(null);
            }
            Duration lifetime = Duration.ofSeconds(Math.max(0, answer.path("expires_in").asLong(0)));
            token = new AccessToken(value, clock.instant().plus(lifetime).minus(TOKEN_MARGIN));
            return value;
        }
    }

    // ------------------------------------------------------------------ JSON

    private JsonNode read(HttpResponse<String> response) {
        try {
            return json.readTree(response.body());
        } catch (JacksonException e) {
            log.warn("PayPal sent an answer that is not JSON");
            throw unavailable(e);
        }
    }

    private PayPalOrder order(JsonNode node) {
        JsonNode unit = node.path("purchase_units").path(0);
        JsonNode captureNode = unit.path("payments").path("captures").path(0);
        Capture capture = captureNode.isObject()
                ? new Capture(captureNode.path("id").asString(""), captureNode.path("status").asString(""),
                        money(captureNode.path("amount")))
                : null;
        Money amount = unit.path("amount").isObject() ? money(unit.path("amount")) : null;
        if (amount == null && capture != null) {
            amount = capture.amount();
        }
        String id = node.path("id").asString("");
        if (id.isBlank() || amount == null) {
            log.warn("PayPal sent an order without id or amount");
            throw unavailable(null);
        }
        return new PayPalOrder(id, node.path("status").asString(""), amount, capture);
    }

    private static Money money(JsonNode amount) {
        return PayPalAmounts.money(amount.path("value").asString(null), amount.path("currency_code").asString(null));
    }

    private List<String> issues(String body) {
        List<String> issues = new ArrayList<>();
        try {
            JsonNode error = json.readTree(body == null || body.isBlank() ? "{}" : body);
            for (JsonNode detail : error.path("details")) {
                issues.add(detail.path("issue").asString(""));
            }
            if (issues.isEmpty() && error.path("name").isString()) {
                issues.add(error.path("name").asString());
            }
        } catch (JacksonException e) {
            // Not JSON: nothing to report beyond the status code.
        }
        return issues;
    }

    private String debugId(String body) {
        try {
            return json.readTree(body == null || body.isBlank() ? "{}" : body).path("debug_id").asString("");
        } catch (JacksonException e) {
            return "";
        }
    }

    private static String checked(String paypalOrderId) {
        if (paypalOrderId == null || !PAYPAL_ID.matcher(paypalOrderId).matches()) {
            throw new IllegalArgumentException("Not a PayPal order id");
        }
        return paypalOrderId;
    }

    private static ServiceUnavailableException unavailable(Throwable cause) {
        return new ServiceUnavailableException(
                "PAYMENT_PROVIDER_UNAVAILABLE", "The payment service is not available right now. Please try again.", cause);
    }

    private record AccessToken(String value, Instant expiresAt) {}
}
