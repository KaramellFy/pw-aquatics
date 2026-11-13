package com.pwaquatics.shop.payment;

import static com.pwaquatics.shop.support.AuthFixture.json;
import static com.pwaquatics.shop.support.AuthFixture.uniqueEmail;
import static com.pwaquatics.shop.support.TestRequests.uniqueClientIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pwaquatics.shop.common.error.ServiceUnavailableException;
import com.pwaquatics.shop.customer.AddressApiTest;
import com.pwaquatics.shop.notification.domain.OutboxMessage;
import com.pwaquatics.shop.order.application.OrderExpiry;
import com.pwaquatics.shop.payment.application.PaymentWebhookCleanupJob;
import com.pwaquatics.shop.payment.application.StripeGateway.PaymentIntentData;
import com.pwaquatics.shop.support.AuthFixture;
import com.pwaquatics.shop.support.FakeStripeGateway;
import com.pwaquatics.shop.support.IntegrationTest;
import com.stripe.net.Webhook;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Card payments through Stripe end to end, with Stripe's API replaced by {@link FakeStripeGateway}
 * and webhooks signed for real with the test secret. What must never break: an order is paid
 * only through a signed webhook for the full stored total, a repeated webhook changes nothing,
 * and a cancelled order cannot be paid.
 */
@IntegrationTest
@Sql(scripts = "/order/order-test-data.sql", executionPhase = ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = "/order/order-test-cleanup.sql", executionPhase = ExecutionPhase.AFTER_TEST_CLASS)
class StripePaymentApiTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret_for_tests"; // application-test.yml
    private static final String ALPHA_SINGLE = "22222222-0000-0000-0000-000000000201"; // 3.00, live animal
    private static final String ALPHA_PRODUCT = "22222222-0000-0000-0000-000000000101";
    private static final String LIVE = "TEST_LIVE"; // 9.90 shipping
    private static final long ONE_ANIMAL_TOTAL = 1290;

    @Autowired MockMvc mvc;
    @Autowired AuthFixture auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired FakeStripeGateway stripe;
    @Autowired OrderExpiry expiry;
    @Autowired PaymentWebhookCleanupJob webhookCleanup;

    @AfterEach
    void stripeIsBack() {
        stripe.setDown(false);
    }

    // ------------------------------------------------------------------ access

    @Test
    void cardPaymentNeedsASignedInCustomerAndACsrfToken() throws Exception {
        mvc.perform(json(post("/api/v1/payments/stripe/intents"), "{\"orderNumber\":\"PW-2026-000001\"}"))
                .andExpect(status().isUnauthorized());

        Customer customer = customer();
        String order = placeCardOrder(customer);
        mvc.perform(post("/api/v1/payments/stripe/intents").cookie(customer.session())
                        .contentType("application/json")
                        .content("{\"orderNumber\":\"%s\"}".formatted(order)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        assertThat(stripe.intentOf(orderId(order))).isNull();
    }

    @Test
    void webhooksNeedNoSessionButAValidStripeSignature() throws Exception {
        Customer customer = customer();
        String order = placeCardOrder(customer);
        String intent = startPayment(customer, order).clientSecretIntentId();
        String event = intentEvent("payment_intent.succeeded", intent, ONE_ANIMAL_TOTAL, "");

        // No signature, a signature with another secret, a signature for a different body.
        mvc.perform(post("/api/v1/webhooks/stripe").contentType("application/json").content(event)
                        .with(uniqueClientIp()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
        mvc.perform(post("/api/v1/webhooks/stripe").contentType("application/json").content(event)
                        .header("Stripe-Signature", Webhook.Signature.generateSignatureHeader(event, "whsec_other"))
                        .with(uniqueClientIp()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
        String signedForOther = Webhook.Signature.generateSignatureHeader(event.replace("1290", "1"), WEBHOOK_SECRET);
        mvc.perform(post("/api/v1/webhooks/stripe").contentType("application/json").content(event)
                        .header("Stripe-Signature", signedForOther)
                        .with(uniqueClientIp()))
                .andExpect(status().isBadRequest());
        // Signed, but not an event.
        webhook("{\"hello\": \"world\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEBHOOK_INVALID"));

        detail(customer, order).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_webhook_events WHERE payload::text LIKE ?",
                Integer.class, "%" + intent + "%")).isZero();

        // A correctly signed event is accepted without session or CSRF token.
        webhook(event).andExpect(status().isNoContent());
        detail(customer, order).andExpect(jsonPath("$.status").value("PAID"));
    }

    // ------------------------------------------------------------------ paying

    @Test
    void aCardOrderIsPaidOnlyThroughTheSignedWebhookForTheStoredTotal() throws Exception {
        Customer customer = customer();
        add(customer.session(), ALPHA_SINGLE, 1);
        mvc.perform(json(post("/api/v1/checkout/quote"), """
                        {"shippingAddressId": "%s", "shippingMethodCode": "%s"}
                        """.formatted(customer.addressId(), LIVE)).cookie(customer.session()))
                .andExpect(jsonPath("$.paymentMethods").value(contains("STRIPE", "BANK_TRANSFER")));

        // The order: reserved for about an hour, no bank details, a link to pay in the email.
        Instant before = Instant.now();
        MvcResult placed = place(customer, "STRIPE", ONE_ANIMAL_TOTAL)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentMethod").value("STRIPE"))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.paymentInstructions").doesNotExist())
                .andReturn();
        String order = read(placed, "$.orderNumber");
        Instant due = Instant.parse(read(placed, "$.paymentDueAt"));
        assertThat(due).isBetween(before.plus(Duration.ofMinutes(59)), Instant.now().plus(Duration.ofMinutes(61)));
        String confirmation = auth.emailsTo(customer.email()).getLast().getPayload().body();
        assertThat(confirmation).contains("Payment by card", "/checkout/pay/" + order).doesNotContain("IBAN");
        detail(customer, order)
                .andExpect(jsonPath("$.payableByCard").value(true))
                .andExpect(jsonPath("$.paymentInstructions").doesNotExist());

        // Starting the payment: the amount is the stored total, whatever the request says.
        CardPayment payment = startPayment(customer, order, ", \"amount\": {\"amount\": 1, \"currency\": \"EUR\"}");
        assertThat(payment.amount()).isEqualTo(ONE_ANIMAL_TOTAL);
        assertThat(payment.publishableKey()).isEqualTo("pk_test_fake_for_tests");
        PaymentIntentData intent = stripe.intentOf(orderId(order));
        assertThat(intent.amount().amount()).isEqualTo(ONE_ANIMAL_TOTAL);
        assertThat(intent.amount().currency()).isEqualTo("EUR");
        // A reload or a second attempt reuses the same PaymentIntent.
        assertThat(startPayment(customer, order).clientSecret()).isEqualTo(payment.clientSecret());
        assertThat(paymentRow(order)).containsEntry("status", "CREATED").containsEntry("amount_cents", ONE_ANIMAL_TOTAL);

        // The browser reports success: nothing changes until Stripe's webhook arrives.
        stripe.setStatus(intent.id(), "succeeded");
        detail(customer, order).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        startPayment(customer, order, "", true);

        // Stripe's event also carries personal data, which is not stored.
        String event = intentEvent("payment_intent.succeeded", intent.id(), ONE_ANIMAL_TOTAL, """
                , "receipt_email": "jane@example.com",
                  "shipping": {"name": "Jane Doe", "address": {"line1": "Hidden Street 1", "city": "Berlin"}}
                """);
        webhook(event).andExpect(status().isNoContent());

        detail(customer, order)
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.paymentDueAt").doesNotExist())
                .andExpect(jsonPath("$.payableByCard").value(false))
                .andExpect(jsonPath("$.cancellable").value(false));
        assertThat(paymentRow(order)).containsEntry("status", "SUCCEEDED").containsEntry("method_detail", "card");
        Map<String, Object> stored = jdbc.queryForMap(
                "SELECT payload::text AS payload, processed_at, error FROM payment_webhook_events WHERE event_id = ?",
                eventId(event));
        assertThat(stored.get("processed_at")).isNotNull();
        assertThat(stored.get("error")).isNull();
        assertThat((String) stored.get("payload"))
                .contains(intent.id(), "payment_intent.succeeded", "1290")
                .doesNotContain("jane@example.com", "Jane Doe", "Hidden Street", "Berlin");
        assertThat(paymentReceivedEmails(customer)).hasSize(1);
        assertThat(paymentReceivedEmails(customer).getFirst().getPayload().body()).contains(order, "€12.90");

        // Stripe delivers again: nothing happens twice.
        webhook(event).andExpect(status().isNoContent());
        assertThat(paymentReceivedEmails(customer)).hasSize(1);

        // Paid orders cannot be paid again or cancelled online.
        startPaymentRequest(customer, order, "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ALREADY_PAID"));
        cancel(customer, order)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));

        // Refunds made in the Stripe Dashboard are recorded; the order status stays for the owner to decide.
        webhook(refundEvent(intent.id(), 500)).andExpect(status().isNoContent());
        detail(customer, order)
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_REFUNDED"));
        webhook(refundEvent(intent.id(), ONE_ANIMAL_TOTAL)).andExpect(status().isNoContent());
        detail(customer, order).andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));
        assertThat(paymentRow(order))
                .containsEntry("status", "REFUNDED")
                .containsEntry("refunded_cents", ONE_ANIMAL_TOTAL);
    }

    @Test
    void declinedCardsAndWrongAmountsDoNotMarkTheOrderAsPaid() throws Exception {
        Customer customer = customer();
        String order = placeCardOrder(customer);
        CardPayment payment = startPayment(customer, order);
        String intent = payment.clientSecretIntentId();

        webhook(intentEvent("payment_intent.payment_failed", intent, 0, """
                , "last_payment_error": {"code": "card_declined", "decline_code": "insufficient_funds",
                    "message": "Your card has insufficient funds.",
                    "payment_method": {"billing_details": {"name": "Jane Doe"}, "card": {"last4": "9995"}}}
                """)).andExpect(status().isNoContent());
        assertThat(paymentRow(order))
                .containsEntry("status", "FAILED")
                .containsEntry("failure_code", "insufficient_funds");
        detail(customer, order)
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.payableByCard").value(true));
        // The customer can try another card with the same PaymentIntent.
        assertThat(startPayment(customer, order).clientSecret()).isEqualTo(payment.clientSecret());

        // Stripe reporting a different amount never marks the order as paid.
        String wrongAmount = intentEvent("payment_intent.succeeded", intent, 1, "");
        webhook(wrongAmount).andExpect(status().isNoContent());
        detail(customer, order).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        assertThat(jdbc.queryForObject("SELECT error FROM payment_webhook_events WHERE event_id = ?", String.class,
                eventId(wrongAmount))).isEqualTo("amount mismatch");
        assertThat(paymentReceivedEmails(customer)).isEmpty();

        // Events for PaymentIntents we did not create, and event types we do not use, are acknowledged.
        webhook(intentEvent("payment_intent.succeeded", "pi_from_another_system", 999, ""))
                .andExpect(status().isNoContent());
        String unused = intentEvent("customer.created", intent, 0, "");
        webhook(unused).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT error FROM payment_webhook_events WHERE event_id = ?", String.class,
                eventId(unused))).isEqualTo("ignored");
    }

    @Test
    void cardPaymentIsOnlyForTheCustomersOwnUnpaidCardOrders() throws Exception {
        Customer customer = customer();
        String order = placeCardOrder(customer);

        Customer stranger = customer();
        startPaymentRequest(stranger, order, "").andExpect(status().isNotFound());
        startPaymentRequest(customer, "PW-2026-999999999", "").andExpect(status().isNotFound());
        startPaymentRequest(customer, "not-an-order", "").andExpect(status().isBadRequest());
        assertThat(stripe.intentOf(orderId(order))).isNull();

        add(customer.session(), ALPHA_SINGLE, 1);
        String bankTransfer = read(place(customer, "BANK_TRANSFER", ONE_ANIMAL_TOTAL).andExpect(status().isCreated())
                .andReturn(), "$.orderNumber");
        startPaymentRequest(customer, bankTransfer, "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_METHOD_UNAVAILABLE"));
        detail(customer, bankTransfer).andExpect(jsonPath("$.payableByCard").value(false));

        stripe.setDown(true);
        startPaymentRequest(customer, order, "")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_PROVIDER_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id = ?", Integer.class,
                orderId(order))).isZero();
    }

    // ------------------------------------------------------------------ cancelling and expiry

    @Test
    void cardOrdersExpireAfterTheTimeLimitAndTheirPaymentIsCancelledAtStripe() throws Exception {
        int stockBefore = stock(ALPHA_PRODUCT);

        // Payment started but not completed.
        Customer started = customer();
        String startedOrder = placeCardOrder(started);
        startPayment(started, startedOrder);
        // Never opened the payment page.
        Customer neverStarted = customer();
        String neverStartedOrder = placeCardOrder(neverStarted);
        // Paid at Stripe, but the webhook has not arrived yet.
        Customer paidLate = customer();
        String paidLateOrder = placeCardOrder(paidLate);
        String paidLateIntent = startPayment(paidLate, paidLateOrder).clientSecretIntentId();
        stripe.setStatus(paidLateIntent, "succeeded");

        for (String order : List.of(startedOrder, neverStartedOrder, paidLateOrder)) {
            jdbc.update("UPDATE orders SET payment_due_at = now() - interval '1 minute' WHERE order_number = ?", order);
        }
        assertThat(stock(ALPHA_PRODUCT)).isEqualTo(stockBefore - 3);

        assertThat(expiry.expire(orderId(startedOrder))).isTrue();
        assertThat(expiry.expire(orderId(neverStartedOrder))).isTrue();
        assertThat(expiry.expire(orderId(paidLateOrder))).isFalse();
        assertThat(stock(ALPHA_PRODUCT)).isEqualTo(stockBefore - 1);

        assertThat(stripe.intentOf(orderId(startedOrder)).status()).isEqualTo("canceled");
        assertThat(paymentRow(startedOrder)).containsEntry("status", "CANCELLED");
        detail(started, startedOrder)
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.payableByCard").value(false));
        assertThat(auth.emailsTo(started.email()).getLast().getPayload().body())
                .contains("card payment was not completed in time", "Your card has not been charged");
        startPaymentRequest(started, startedOrder, "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PAYABLE"));

        // The late webhook still marks the order that kept its animals as paid.
        detail(paidLate, paidLateOrder).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        webhook(intentEvent("payment_intent.succeeded", paidLateIntent, ONE_ANIMAL_TOTAL, ""))
                .andExpect(status().isNoContent());
        detail(paidLate, paidLateOrder).andExpect(jsonPath("$.status").value("PAID"));
        assertThat(expiry.expire(orderId(paidLateOrder))).isFalse();
    }

    @Test
    void whenStripeCannotBeReachedTheExpiryTriesAgainLater() throws Exception {
        Customer customer = customer();
        String order = placeCardOrder(customer);
        startPayment(customer, order);
        jdbc.update("UPDATE orders SET payment_due_at = now() - interval '1 minute' WHERE order_number = ?", order);

        stripe.setDown(true);
        assertThatThrownBy(() -> expiry.expire(orderId(order))).isInstanceOf(ServiceUnavailableException.class);
        detail(customer, order).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));

        stripe.setDown(false);
        assertThat(expiry.expire(orderId(order))).isTrue();
        detail(customer, order).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void customersCanCancelACardOrderUntilStripeIsProcessingItsPayment() throws Exception {
        Customer customer = customer();
        String order = placeCardOrder(customer);
        String intent = startPayment(customer, order).clientSecretIntentId();

        stripe.setStatus(intent, "processing");
        cancel(customer, order)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
        detail(customer, order).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));

        // The bank declined after all: now it can be cancelled, and the PaymentIntent with it.
        stripe.setStatus(intent, "requires_payment_method");
        cancel(customer, order)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(stripe.intentOf(orderId(order)).status()).isEqualTo("canceled");
        assertThat(auth.emailsTo(customer.email()).getLast().getPayload().body())
                .contains("cancelled as requested", "Your card has not been charged");

        // Should money arrive for it anyway, the order stays cancelled and the owner is told.
        int shopEmailsBefore = auth.emailsTo("shop@test.local").size();
        webhook(intentEvent("payment_intent.succeeded", intent, ONE_ANIMAL_TOTAL, ""))
                .andExpect(status().isNoContent());
        detail(customer, order).andExpect(jsonPath("$.status").value("CANCELLED"));
        List<OutboxMessage> shopEmails = auth.emailsTo("shop@test.local");
        assertThat(shopEmails).hasSize(shopEmailsBefore + 1);
        assertThat(shopEmails.getLast().getPayload().subject()).isEqualTo("Payment for cancelled order " + order);
        assertThat(shopEmails.getLast().getPayload().body()).doesNotContain(customer.email());
    }

    // ------------------------------------------------------------------ retention

    @Test
    void webhookDeliveriesAreDeletedAfterTheRetentionPeriod() {
        String old = "evt_test_old_" + UUID.randomUUID();
        String recent = "evt_test_recent_" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO payment_webhook_events (id, provider, event_id, event_type, payload, received_at, processed_at)
                VALUES (gen_random_uuid(), 'STRIPE', ?, 'payment_intent.succeeded', '{}', now() - interval '100 days', now()),
                       (gen_random_uuid(), 'STRIPE', ?, 'payment_intent.succeeded', '{}', now() - interval '10 days', now())
                """, old, recent);

        assertThat(webhookCleanup.purge()).isPositive();
        assertThat(jdbc.queryForList("SELECT event_id FROM payment_webhook_events WHERE event_id IN (?, ?)",
                String.class, old, recent)).containsExactly(recent);
    }

    // ------------------------------------------------------------------ helpers

    private record Customer(String email, Cookie session, String addressId) {}

    private record CardPayment(String clientSecret, String publishableKey, long amount) {

        /** Stripe's client secrets start with the PaymentIntent id. */
        String clientSecretIntentId() {
            return clientSecret.substring(0, clientSecret.indexOf("_secret_"));
        }
    }

    private Customer customer() throws Exception {
        String email = uniqueEmail();
        Cookie session = auth.registeredSession(email);
        MvcResult address = mvc.perform(json(post("/api/v1/account/addresses"), AddressApiTest.ADDRESS).cookie(session))
                .andExpect(status().isCreated())
                .andReturn();
        return new Customer(email, session, read(address, "$.id"));
    }

    private void add(Cookie session, String variantId, int quantity) throws Exception {
        mvc.perform(json(post("/api/v1/cart/items"),
                        "{\"variantId\":\"%s\",\"quantity\":%d}".formatted(variantId, quantity)).cookie(session))
                .andExpect(status().isOk());
    }

    /** One animal, to pay by card. */
    private String placeCardOrder(Customer customer) throws Exception {
        add(customer.session(), ALPHA_SINGLE, 1);
        return read(place(customer, "STRIPE", ONE_ANIMAL_TOTAL).andExpect(status().isCreated()).andReturn(),
                "$.orderNumber");
    }

    private ResultActions place(Customer customer, String paymentMethod, long expectedTotal) throws Exception {
        return mvc.perform(json(post("/api/v1/checkout/orders"), """
                        {"shippingAddressId": "%s", "shippingMethodCode": "%s", "paymentMethod": "%s",
                         "acceptedTerms": true, "acceptedLiveAnimalConditions": true,
                         "expectedTotal": {"amount": %d, "currency": "EUR"}}
                        """.formatted(customer.addressId(), LIVE, paymentMethod, expectedTotal))
                .cookie(customer.session())
                .header("Idempotency-Key", UUID.randomUUID().toString()));
    }

    private ResultActions startPaymentRequest(Customer customer, String orderNumber, String extraJson) throws Exception {
        return mvc.perform(json(post("/api/v1/payments/stripe/intents"),
                        "{\"orderNumber\": \"%s\"%s}".formatted(orderNumber, extraJson))
                .cookie(customer.session()));
    }

    private CardPayment startPayment(Customer customer, String orderNumber) throws Exception {
        return startPayment(customer, orderNumber, "");
    }

    private CardPayment startPayment(Customer customer, String orderNumber, String extraJson) throws Exception {
        MvcResult result = startPaymentRequest(customer, orderNumber, extraJson)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.alreadyPaid").value(false))
                .andReturn();
        return new CardPayment(
                read(result, "$.clientSecret"),
                read(result, "$.publishableKey"),
                this.<Number>read(result, "$.amount.amount").longValue());
    }

    /** Stripe already has the money: no form to show any more. */
    private void startPayment(Customer customer, String orderNumber, String extraJson, boolean alreadyPaid)
            throws Exception {
        startPaymentRequest(customer, orderNumber, extraJson)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyPaid").value(alreadyPaid))
                .andExpect(jsonPath("$.clientSecret").doesNotExist());
    }

    private ResultActions detail(Customer customer, String orderNumber) throws Exception {
        return mvc.perform(get("/api/v1/account/orders/" + orderNumber).cookie(customer.session()))
                .andExpect(status().isOk());
    }

    private ResultActions cancel(Customer customer, String orderNumber) throws Exception {
        return mvc.perform(post("/api/v1/account/orders/" + orderNumber + "/cancel")
                .cookie(customer.session())
                .with(csrf())
                .with(uniqueClientIp()));
    }

    private ResultActions webhook(String payload) throws Exception {
        return mvc.perform(post("/api/v1/webhooks/stripe")
                .contentType("application/json")
                .content(payload)
                .header("Stripe-Signature", Webhook.Signature.generateSignatureHeader(payload, WEBHOOK_SECRET))
                .with(uniqueClientIp()));
    }

    /** A PaymentIntent event as Stripe sends it (shortened), with a new event id. */
    private static String intentEvent(String type, String intentId, long amountReceived, String extraFields) {
        return """
                {"id": "evt_test_%s", "object": "event", "type": "%s", "created": 1763020800, "livemode": false,
                 "data": {"object": {"id": "%s", "object": "payment_intent", "amount": 1290,
                   "amount_received": %d, "currency": "eur", "status": "succeeded",
                   "payment_method_types": ["card"]%s}}}
                """.formatted(UUID.randomUUID(), type, intentId, amountReceived, extraFields);
    }

    /** A refund as Stripe reports it: on the charge, with everything refunded so far. */
    private static String refundEvent(String intentId, long amountRefunded) {
        return """
                {"id": "evt_test_%s", "object": "event", "type": "charge.refunded", "created": 1763020800,
                 "livemode": false,
                 "data": {"object": {"id": "ch_test_1", "object": "charge", "payment_intent": "%s", "amount": 1290,
                   "amount_refunded": %d, "currency": "eur",
                   "billing_details": {"name": "Jane Doe", "email": "jane@example.com"},
                   "payment_method_details": {"card": {"last4": "4242"}}}}}
                """.formatted(UUID.randomUUID(), intentId, amountRefunded);
    }

    private static String eventId(String payload) {
        return JsonPath.read(payload, "$.id");
    }

    private List<OutboxMessage> paymentReceivedEmails(Customer customer) {
        return auth.emailsTo(customer.email()).stream()
                .filter(message -> message.getPayload().subject().startsWith("Payment received"))
                .toList();
    }

    private Map<String, Object> paymentRow(String orderNumber) {
        return jdbc.queryForMap("""
                SELECT p.status, p.amount_cents, p.refunded_cents, p.method_detail, p.failure_code
                FROM payments p JOIN orders o ON o.id = p.order_id WHERE o.order_number = ?
                """, orderNumber);
    }

    private int stock(String productId) {
        return jdbc.queryForObject("SELECT stock_units FROM products WHERE id = ?::uuid", Integer.class, productId);
    }

    private UUID orderId(String orderNumber) {
        return jdbc.queryForObject("SELECT id FROM orders WHERE order_number = ?", UUID.class, orderNumber);
    }

    private <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
