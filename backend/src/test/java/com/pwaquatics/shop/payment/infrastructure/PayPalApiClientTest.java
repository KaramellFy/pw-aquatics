package com.pwaquatics.shop.payment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.common.error.ServiceUnavailableException;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.payment.application.PayPalGateway.CaptureRefused;
import com.pwaquatics.shop.payment.application.PayPalGateway.PayPalOrder;
import com.pwaquatics.shop.payment.application.PayPalGateway.WebhookSignature;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The PayPal client against a stand-in for PayPal's REST API (it never reaches PayPal). */
class PayPalApiClientTest {

    private static final AppProperties.PayPal ACCOUNT = new AppProperties.PayPal(
            "client-id", "client-secret", "WEBHOOK-ID", "sandbox", Duration.ofSeconds(5));
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UUID ORDER_ID = UUID.fromString("0196f0c2-0000-7000-8000-000000000001");

    private HttpServer paypal;
    private PayPalApiClient client;
    private final AtomicInteger signIns = new AtomicInteger();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    /** Answers by method and path: status and body. */
    private final Map<String, Answer> answers = new ConcurrentHashMap<>();

    private record Request(String method, String path, Map<String, String> headers, String body) {}

    private record Answer(int status, String body) {}

    @BeforeEach
    void startPayPal() throws IOException {
        paypal = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        paypal.createContext("/v1/oauth2/token", exchange -> {
            signIns.incrementAndGet();
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            String expected = "Basic " + java.util.Base64.getEncoder()
                    .encodeToString("client-id:client-secret".getBytes(StandardCharsets.UTF_8));
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (!expected.equals(auth) || !"grant_type=client_credentials".equals(body)) {
                send(exchange, 401, "{\"error\":\"invalid_client\"}");
                return;
            }
            send(exchange, 200, "{\"access_token\":\"token-" + signIns.get() + "\",\"expires_in\":32400}");
        });
        paypal.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> headers = new ConcurrentHashMap<>();
            exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values.getFirst()));
            String path = exchange.getRequestURI().getPath();
            requests.add(new Request(exchange.getRequestMethod(), path, headers, body));
            Answer answer = answers.getOrDefault(exchange.getRequestMethod() + " " + path, new Answer(404, "{}"));
            send(exchange, answer.status(), answer.body());
        });
        paypal.start();
        client = new PayPalApiClient(
                ACCOUNT, URI.create("http://127.0.0.1:" + paypal.getAddress().getPort()), JSON, Clock.systemUTC());
    }

    @AfterEach
    void stopPayPal() {
        paypal.stop(0);
    }

    @Test
    void createsTheOrderForTheTotalWithoutAskingForAnAddress() {
        answers.put("POST /v2/checkout/orders", new Answer(201, order("5O190127TN364715T", "CREATED", null)));

        PayPalOrder created = client.createOrder(ORDER_ID, "PW-2026-000123", new Money(1290, "EUR"), "de");

        assertThat(created).isEqualTo(new PayPalOrder("5O190127TN364715T", "CREATED", new Money(1290, "EUR"), null));
        Request request = requests.getFirst();
        assertThat(request.headers())
                .containsEntry("authorization", "Bearer token-1")
                .containsEntry("paypal-request-id", "order-" + ORDER_ID)
                .containsEntry("content-type", "application/json");
        JsonNode body = JSON.readTree(request.body());
        assertThat(body.path("intent").asString()).isEqualTo("CAPTURE");
        JsonNode unit = body.path("purchase_units").path(0);
        assertThat(unit.path("amount").path("value").asString()).isEqualTo("12.90");
        assertThat(unit.path("amount").path("currency_code").asString()).isEqualTo("EUR");
        assertThat(unit.path("custom_id").asString()).isEqualTo("PW-2026-000123");
        JsonNode experience = body.path("payment_source").path("paypal").path("experience_context");
        assertThat(experience.path("shipping_preference").asString()).isEqualTo("NO_SHIPPING");
        assertThat(experience.path("locale").asString()).isEqualTo("de-DE");
        assertThat(experience.path("user_action").asString()).isEqualTo("PAY_NOW");

        // The access token is reused for the next call.
        answers.put("GET /v2/checkout/orders/5O190127TN364715T", new Answer(200, order("5O190127TN364715T", "APPROVED", null)));
        assertThat(client.getOrder("5O190127TN364715T").status()).isEqualTo("APPROVED");
        assertThat(signIns).hasValue(1);
    }

    @Test
    void capturesAndReadsTheResultFromPayPal() {
        answers.put("POST /v2/checkout/orders/5O190127TN364715T/capture",
                new Answer(201, order("5O190127TN364715T", "COMPLETED", "3C679366HH908993F")));

        PayPalOrder captured = client.capture("5O190127TN364715T", ORDER_ID);

        assertThat(captured.capture().id()).isEqualTo("3C679366HH908993F");
        assertThat(captured.capture().isCompleted()).isTrue();
        assertThat(captured.capture().amount()).isEqualTo(new Money(1290, "EUR"));
        assertThat(requests.getFirst().headers()).containsEntry("paypal-request-id", "order-" + ORDER_ID + "-capture");
    }

    @Test
    void aDeclinedFundingSourceIsReportedAndAnEarlierCaptureIsReturned() {
        answers.put("POST /v2/checkout/orders/AAA111/capture", new Answer(422, """
                {"name": "UNPROCESSABLE_ENTITY", "details": [{"issue": "INSTRUMENT_DECLINED"}], "debug_id": "abc"}
                """));
        assertThatThrownBy(() -> client.capture("AAA111", ORDER_ID))
                .isInstanceOfSatisfying(CaptureRefused.class, e -> assertThat(e.issue()).isEqualTo("INSTRUMENT_DECLINED"));

        answers.put("POST /v2/checkout/orders/BBB222/capture", new Answer(422, """
                {"name": "UNPROCESSABLE_ENTITY", "details": [{"issue": "ORDER_ALREADY_CAPTURED"}]}
                """));
        answers.put("GET /v2/checkout/orders/BBB222", new Answer(200, order("BBB222", "COMPLETED", "CAP1")));
        assertThat(client.capture("BBB222", ORDER_ID).capture().id()).isEqualTo("CAP1");
    }

    @Test
    void anExpiredTokenIsRenewedOnceAndOtherErrorsMeanPayPalIsUnavailable() {
        answers.put("GET /v2/checkout/orders/AAA111", new Answer(401, "{\"name\":\"AUTHENTICATION_FAILURE\"}"));
        assertThatThrownBy(() -> client.getOrder("AAA111")).isInstanceOf(ServiceUnavailableException.class);
        assertThat(signIns).hasValue(2);

        answers.put("GET /v2/checkout/orders/BBB222", new Answer(500, "{\"name\":\"INTERNAL_SERVER_ERROR\"}"));
        assertThatThrownBy(() -> client.getOrder("BBB222"))
                .isInstanceOfSatisfying(ServiceUnavailableException.class,
                        e -> assertThat(e.code()).isEqualTo("PAYMENT_PROVIDER_UNAVAILABLE"));

        // Ids that are not PayPal's never end up in a URL.
        assertThatThrownBy(() -> client.getOrder("../v1/oauth2/token")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void webhookDeliveriesAreCheckedByPayPalWithTheBodyAsReceived() {
        String event = "{\"id\":\"WH-1\",\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\",  \"resource\":{\"amount\":{\"value\":\"12.90\"}}}";
        WebhookSignature signature = new WebhookSignature(
                "SHA256withRSA", "https://api.sandbox.paypal.com/v1/notifications/certs/CERT", "transmission-1",
                "signature\"with quote", "2026-11-17T10:00:00Z");

        answers.put("POST /v1/notifications/verify-webhook-signature", new Answer(200, "{\"verification_status\":\"SUCCESS\"}"));
        assertThat(client.isGenuine(signature, event)).isTrue();
        String sent = requests.getLast().body();
        // The event is passed on byte for byte (spacing included), next to our webhook id.
        assertThat(sent).endsWith(",\"webhook_event\":" + event + "}");
        JsonNode body = JSON.readTree(sent);
        assertThat(body.path("webhook_id").asString()).isEqualTo("WEBHOOK-ID");
        assertThat(body.path("transmission_sig").asString()).isEqualTo("signature\"with quote");
        assertThat(body.path("webhook_event").path("id").asString()).isEqualTo("WH-1");

        answers.put("POST /v1/notifications/verify-webhook-signature", new Answer(200, "{\"verification_status\":\"FAILURE\"}"));
        assertThat(client.isGenuine(signature, event)).isFalse();
        answers.put("POST /v1/notifications/verify-webhook-signature", new Answer(400, "{\"name\":\"VALIDATION_ERROR\"}"));
        assertThat(client.isGenuine(signature, event)).isFalse();
    }

    private static String order(String id, String status, String captureId) {
        String payments = captureId == null ? "" : """
                , "payments": {"captures": [{"id": "%s", "status": "COMPLETED",
                    "amount": {"currency_code": "EUR", "value": "12.90"}}]}
                """.formatted(captureId);
        return """
                {"id": "%s", "status": "%s",
                 "payer": {"email_address": "jane@example.com"},
                 "purchase_units": [{"reference_id": "PW-2026-000123",
                   "amount": {"currency_code": "EUR", "value": "12.90"}%s}]}
                """.formatted(id, status, payments);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
