package com.pwaquatics.shop.payment;

import static com.pwaquatics.shop.support.AuthFixture.json;
import static com.pwaquatics.shop.support.AuthFixture.uniqueEmail;
import static com.pwaquatics.shop.support.TestRequests.uniqueClientIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.customer.AddressApiTest;
import com.pwaquatics.shop.notification.domain.OutboxMessage;
import com.pwaquatics.shop.support.AuthFixture;
import com.pwaquatics.shop.support.FakePayPalGateway;
import com.pwaquatics.shop.support.FakeStripeGateway;
import com.pwaquatics.shop.support.IntegrationTest;
import com.stripe.net.Webhook;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Refunds from the admin order page (docs/API.md §3.5) with the providers replaced by
 * {@link FakeStripeGateway} and {@link FakePayPalGateway}. What must never break: no refund
 * beyond what was paid, a repeated request never pays twice, a refund the provider refuses leaves
 * no trace, and a refund that fails later is taken back and reported to the owner.
 */
@IntegrationTest
@Sql(scripts = "/order/order-test-data.sql", executionPhase = ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = "/order/order-test-cleanup.sql", executionPhase = ExecutionPhase.AFTER_TEST_CLASS)
class AdminRefundApiTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret_for_tests"; // application-test.yml
    private static final String ALPHA_SINGLE = "22222222-0000-0000-0000-000000000201"; // 3.00, live animal
    private static final String LIVE = "TEST_LIVE"; // 9.90 shipping
    private static final long ONE_ANIMAL_TOTAL = 1290;
    private static final String ADMIN_ORDERS = "/api/v1/admin/orders";
    private static final String SHOP_INBOX = "shop@test.local";

    @Autowired MockMvc mvc;
    @Autowired AuthFixture auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired FakeStripeGateway stripe;
    @Autowired FakePayPalGateway paypal;

    private Cookie admin;

    @BeforeEach
    void signInAdmin() throws Exception {
        admin = auth.adminSession(uniqueEmail());
    }

    @AfterEach
    void providersBackToNormal() {
        stripe.resetRefunds();
        paypal.reset();
    }

    // ------------------------------------------------------------------ access

    @Test
    void onlyAdministratorsCanRefund() throws Exception {
        Customer customer = customer();
        String order = paidCardOrder(customer);
        String body = refundBody(500, ONE_ANIMAL_TOTAL, "");

        mvc.perform(json(post(ADMIN_ORDERS + "/" + order + "/refunds"), body).header("Idempotency-Key", key()))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(post(ADMIN_ORDERS + "/" + order + "/refunds"), body)
                        .header("Idempotency-Key", key())
                        .cookie(customer.session()))
                .andExpect(status().isForbidden());
        mvc.perform(post(ADMIN_ORDERS + "/" + order + "/refunds").contentType("application/json").content(body)
                        .header("Idempotency-Key", key())
                        .cookie(admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        // Without a request key, or with one that is too short.
        mvc.perform(json(post(ADMIN_ORDERS + "/" + order + "/refunds"), body).cookie(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post(ADMIN_ORDERS + "/" + order + "/refunds"), body).header("Idempotency-Key", "short")
                        .cookie(admin))
                .andExpect(status().isBadRequest());

        assertThat(stripe.refundCalls()).noneMatch(call -> call.paymentIntentId().equals(intentOf(order)));
        assertThat(paymentRow(order)).containsEntry("refunded_cents", 0L);
    }

    // ------------------------------------------------------------------ Stripe

    @Test
    void aCardPaymentIsRefundedInPartsThroughStripe() throws Exception {
        Customer customer = customer();
        String order = paidCardOrder(customer);
        adminView(order)
                .andExpect(jsonPath("$.actions.refund").value(true))
                .andExpect(jsonPath("$.payment.refundable.amount").value(ONE_ANIMAL_TOTAL))
                .andExpect(jsonPath("$.payment.refunds").isEmpty());

        // Two animals arrived dead: part of the money goes back, with a word to the customer.
        String key = key();
        refund(order, key, refundBody(500, ONE_ANIMAL_TOTAL,
                ", \"reason\": \"Arrived dead\", \"customerMessage\": \"Sorry about the shrimp.\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.paymentStatus").value("PARTIALLY_REFUNDED"))
                .andExpect(jsonPath("$.payment.status").value("PARTIALLY_REFUNDED"))
                .andExpect(jsonPath("$.payment.refunded.amount").value(500))
                .andExpect(jsonPath("$.payment.refundable.amount").value(ONE_ANIMAL_TOTAL - 500))
                .andExpect(jsonPath("$.payment.refunds", hasSize(1)))
                .andExpect(jsonPath("$.payment.refunds[0].amount.amount").value(500))
                .andExpect(jsonPath("$.payment.refunds[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.payment.refunds[0].reason").value("Arrived dead"))
                .andExpect(jsonPath("$.payment.refunds[0].reference").value(startsWith("re_test_")))
                .andExpect(jsonPath("$.payment.refunds[0].requestedBy").isNotEmpty())
                .andExpect(jsonPath("$.actions.refund").value(true));

        assertThat(stripe.refundCalls()).last()
                .isEqualTo(new FakeStripeGateway.RefundCall(intentOf(order), Money.eur(500), key));
        OutboxMessage email = auth.emailsTo(customer.email()).getLast();
        assertThat(email.getPayload().subject()).isEqualTo("Refund for your order " + order);
        assertThat(email.getPayload().body())
                .contains("we have refunded €5.00", "goes back to the card", "Sorry about the shrimp.");
        assertThat(auditActions(order)).contains("ORDER_REFUNDED_BY_SHOP");
        detail(customer, order).andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_REFUNDED"));

        // Stripe's own report of the same refund changes nothing.
        stripeWebhook(chargeRefundedEvent(intentOf(order), 500)).andExpect(status().isNoContent());
        assertThat(paymentRow(order)).containsEntry("refunded_cents", 500L);

        // The rest, without an email.
        refund(order, key(), refundBody(ONE_ANIMAL_TOTAL - 500, ONE_ANIMAL_TOTAL - 500, ", \"notifyCustomer\": false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.paymentStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.payment.refundable.amount").value(0))
                .andExpect(jsonPath("$.payment.refunds", hasSize(2)))
                .andExpect(jsonPath("$.actions.refund").value(false));
        assertThat(auth.emailsTo(customer.email()).getLast().getId()).isEqualTo(email.getId());

        // Nothing is left.
        refund(order, key(), refundBody(1, 0, ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_REFUNDABLE"));
    }

    @Test
    void aRepeatedRequestMakesNoSecondRefund() throws Exception {
        Customer customer = customer();
        String order = paidCardOrder(customer);
        String key = key();
        int emailsBefore = auth.emailsTo(customer.email()).size();

        refund(order, key, refundBody(300, ONE_ANIMAL_TOTAL, "")).andExpect(status().isOk());
        // A double click or a retry after a lost answer: same key, same refund.
        refund(order, key, refundBody(300, ONE_ANIMAL_TOTAL, ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.refunded.amount").value(300))
                .andExpect(jsonPath("$.payment.refunds", hasSize(1)));

        assertThat(stripe.refundCalls()).filteredOn(call -> call.idempotencyKey().equals(key)).hasSize(1);
        assertThat(auth.emailsTo(customer.email())).hasSize(emailsBefore + 1);
        assertThat(auditActions(order)).filteredOn("ORDER_REFUNDED_BY_SHOP"::equals).hasSize(1);

        // The same key for a different refund is a mistake, not a repeat.
        refund(order, key, refundBody(200, ONE_ANIMAL_TOTAL - 300, ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(paymentRow(order)).containsEntry("refunded_cents", 300L);
    }

    @Test
    void theAmountMustFitWhatIsLeftAndWhatTheOwnerSaw() throws Exception {
        Customer customer = customer();
        String order = paidCardOrder(customer);

        refund(order, key(), refundBody(ONE_ANIMAL_TOTAL + 1, ONE_ANIMAL_TOTAL, ""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REFUND_AMOUNT_INVALID"));
        refund(order, key(), refundBody(0, ONE_ANIMAL_TOTAL, ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // Meanwhile someone refunded 2.00 in the Stripe Dashboard; the owner still sees 12.90.
        stripeWebhook(chargeRefundedEvent(intentOf(order), 200)).andExpect(status().isNoContent());
        refund(order, key(), refundBody(500, ONE_ANIMAL_TOTAL, ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUNDS_CHANGED"));
        adminView(order).andExpect(jsonPath("$.payment.refundable.amount").value(ONE_ANIMAL_TOTAL - 200));

        assertThat(stripe.refundCalls()).noneMatch(call -> call.paymentIntentId().equals(intentOf(order)));
        assertThat(refundRows(order)).isZero();
    }

    @Test
    void aRefundStripeRefusesLeavesNoTrace() throws Exception {
        Customer customer = customer();
        String order = paidCardOrder(customer);
        int emailsBefore = auth.emailsTo(customer.email()).size();

        stripe.refuseNextRefund("charge_disputed");
        refund(order, key(), refundBody(500, ONE_ANIMAL_TOTAL, ""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REFUND_REFUSED"))
                .andExpect(jsonPath("$.detail").value("Stripe refused the refund (charge_disputed)."));

        assertThat(refundRows(order)).isZero();
        assertThat(paymentRow(order)).containsEntry("refunded_cents", 0L).containsEntry("status", "SUCCEEDED");
        assertThat(auth.emailsTo(customer.email())).hasSize(emailsBefore);
        assertThat(auditActions(order)).doesNotContain("ORDER_REFUNDED_BY_SHOP");

        // Stripe also says no by failing the refund straight away.
        stripe.setRefundStatus("failed");
        refund(order, key(), refundBody(500, ONE_ANIMAL_TOTAL, ""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REFUND_REFUSED"));
        assertThat(refundRows(order)).isZero();
    }

    @Test
    void onlyCardAndPayPalPaymentsThatArrivedCanBeRefunded() throws Exception {
        // A bank transfer is paid back by bank transfer.
        Customer transfer = customer();
        String transferOrder = placeOrder(transfer, "BANK_TRANSFER");
        mvc.perform(json(post(ADMIN_ORDERS + "/" + transferOrder + "/mark-paid"), "{}").cookie(admin))
                .andExpect(status().isOk());
        adminView(transferOrder).andExpect(jsonPath("$.actions.refund").value(false));
        refund(transferOrder, key(), refundBody(500, ONE_ANIMAL_TOTAL, ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_REFUNDABLE"));

        // A card payment that was started but never completed.
        Customer card = customer();
        String cardOrder = placeOrder(card, "STRIPE");
        startCardPayment(card, cardOrder);
        adminView(cardOrder)
                .andExpect(jsonPath("$.actions.refund").value(false))
                .andExpect(jsonPath("$.payment.refundable.amount").value(0));
        refund(cardOrder, key(), refundBody(500, 0, ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_REFUNDABLE"));

        // An unknown order.
        refund("PW-2026-999999", key(), refundBody(500, 500, "")).andExpect(status().isNotFound());
    }

    @Test
    void aPendingRefundCountsAtOnceAndStripeConfirmsItLater() throws Exception {
        Customer customer = customer();
        String order = paidCardOrder(customer);

        stripe.setRefundStatus("pending");
        MvcResult result = refund(order, key(), refundBody(ONE_ANIMAL_TOTAL, ONE_ANIMAL_TOTAL, ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.paymentStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.payment.refunds[0].status").value("PENDING"))
                .andExpect(jsonPath("$.actions.refund").value(false))
                .andReturn();
        String refundId = read(result, "$.payment.refunds[0].reference");

        stripeWebhook(refundEvent("refund.updated", refundId, intentOf(order), 1290, "succeeded", null))
                .andExpect(status().isNoContent());
        adminView(order)
                .andExpect(jsonPath("$.payment.refunds[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.payment.refunded.amount").value(ONE_ANIMAL_TOTAL));
    }

    @Test
    void aRefundThatFailsLaterIsTakenBackAndTheOwnerIsTold() throws Exception {
        Customer customer = customer();
        String order = paidCardOrder(customer);
        MvcResult result = refund(order, key(), refundBody(500, ONE_ANIMAL_TOTAL, ""))
                .andExpect(status().isOk())
                .andReturn();
        String refundId = read(result, "$.payment.refunds[0].reference");
        int shopEmailsBefore = auth.emailsTo(SHOP_INBOX).size();

        // The card account was closed: the money is back in the shop's Stripe balance.
        String failed = refundEvent("refund.failed", refundId, intentOf(order), 500, "failed", "expired_or_canceled_card");
        stripeWebhook(failed).andExpect(status().isNoContent());
        adminView(order)
                .andExpect(jsonPath("$.order.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.payment.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.payment.refunded.amount").value(0))
                .andExpect(jsonPath("$.payment.refundable.amount").value(ONE_ANIMAL_TOTAL))
                .andExpect(jsonPath("$.payment.refunds[0].status").value("FAILED"))
                .andExpect(jsonPath("$.payment.refunds[0].failureCode").value("expired_or_canceled_card"))
                .andExpect(jsonPath("$.actions.refund").value(true));
        List<OutboxMessage> shopEmails = auth.emailsTo(SHOP_INBOX);
        assertThat(shopEmails).hasSize(shopEmailsBefore + 1);
        assertThat(shopEmails.getLast().getPayload().subject()).isEqualTo("Refund failed for order " + order);
        assertThat(shopEmails.getLast().getPayload().body()).contains("€5.00").doesNotContain(customer.email());
        assertThat(auditActions(order)).contains("ORDER_REFUND_FAILED");

        // Stripe repeats its events; the second report changes nothing.
        stripeWebhook(refundEvent("charge.refund.updated", refundId, intentOf(order), 500, "failed", null))
                .andExpect(status().isNoContent());
        assertThat(auth.emailsTo(SHOP_INBOX)).hasSize(shopEmailsBefore + 1);
        // Only ids, amounts, statuses and the failure reason are kept of the event.
        assertThat(jdbc.queryForObject("SELECT payload::text FROM payment_webhook_events WHERE event_id = ?",
                        String.class, (String) JsonPath.read(failed, "$.id")))
                .contains(refundId, "expired_or_canceled_card")
                .doesNotContain("Jane");
    }

    // ------------------------------------------------------------------ PayPal

    @Test
    void aPayPalPaymentIsRefundedThroughPayPal() throws Exception {
        Customer customer = customer();
        String order = paidPayPalOrder(customer);
        String capture = (String) paymentRow(order).get("capture_reference");
        String key = key();

        MvcResult result = refund(order, key, refundBody(500, ONE_ANIMAL_TOTAL, ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.paymentStatus").value("PARTIALLY_REFUNDED"))
                .andExpect(jsonPath("$.payment.refunds[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.payment.refundable.amount").value(ONE_ANIMAL_TOTAL - 500))
                .andReturn();
        assertThat(paypal.refundCalls()).last()
                .isEqualTo(new FakePayPalGateway.RefundCall(capture, Money.eur(500), key));
        assertThat(auth.emailsTo(customer.email()).getLast().getPayload().body())
                .contains("we have refunded €5.00", "PayPal account");

        // PayPal's report of the same refund changes nothing.
        String refundId = read(result, "$.payment.refunds[0].reference");
        paypalWebhook(paypalRefundEvent(refundId, "COMPLETED", capture, "5.00")).andExpect(status().isNoContent());
        assertThat(paymentRow(order)).containsEntry("refunded_cents", 500L);

        // PayPal refuses a refund after its time limit.
        paypal.refuseNextRefund("REFUND_TIME_LIMIT_EXCEEDED");
        refund(order, key(), refundBody(500, ONE_ANIMAL_TOTAL - 500, ""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REFUND_REFUSED"))
                .andExpect(jsonPath("$.detail").value("PayPal refused the refund (REFUND_TIME_LIMIT_EXCEEDED)."));
        assertThat(refundRows(order)).isOne();
    }

    @Test
    void aPendingPayPalRefundIsCompletedByPayPalsWebhook() throws Exception {
        Customer customer = customer();
        String order = paidPayPalOrder(customer);
        String capture = (String) paymentRow(order).get("capture_reference");

        paypal.setRefundStatus("PENDING");
        MvcResult result = refund(order, key(), refundBody(ONE_ANIMAL_TOTAL, ONE_ANIMAL_TOTAL, ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.paymentStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.payment.refunds[0].status").value("PENDING"))
                .andReturn();
        String refundId = read(result, "$.payment.refunds[0].reference");

        paypalWebhook(paypalRefundEvent(refundId, "COMPLETED", capture, "12.90")).andExpect(status().isNoContent());
        adminView(order)
                .andExpect(jsonPath("$.payment.refunds[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.payment.refunded.amount").value(ONE_ANIMAL_TOTAL))
                .andExpect(jsonPath("$.order.paymentStatus").value("REFUNDED"));
    }

    // ------------------------------------------------------------------ helpers

    private record Customer(String email, Cookie session, String addressId) {}

    private Customer customer() throws Exception {
        String email = uniqueEmail();
        Cookie session = auth.registeredSession(email);
        MvcResult address = mvc.perform(json(post("/api/v1/account/addresses"), AddressApiTest.ADDRESS).cookie(session))
                .andExpect(status().isCreated())
                .andReturn();
        return new Customer(email, session, read(address, "$.id"));
    }

    /** One animal, placed for the given payment method. */
    private String placeOrder(Customer customer, String paymentMethod) throws Exception {
        mvc.perform(json(post("/api/v1/cart/items"), "{\"variantId\":\"%s\",\"quantity\":1}".formatted(ALPHA_SINGLE))
                        .cookie(customer.session()))
                .andExpect(status().isOk());
        MvcResult placed = mvc.perform(json(post("/api/v1/checkout/orders"), """
                        {"shippingAddressId": "%s", "shippingMethodCode": "%s", "paymentMethod": "%s",
                         "acceptedTerms": true, "acceptedLiveAnimalConditions": true,
                         "expectedTotal": {"amount": %d, "currency": "EUR"}}
                        """.formatted(customer.addressId(), LIVE, paymentMethod, ONE_ANIMAL_TOTAL))
                        .cookie(customer.session())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isCreated())
                .andReturn();
        return read(placed, "$.orderNumber");
    }

    /** @return the PaymentIntent id */
    private String startCardPayment(Customer customer, String order) throws Exception {
        MvcResult started = mvc.perform(json(post("/api/v1/payments/stripe/intents"),
                        "{\"orderNumber\": \"%s\"}".formatted(order)).cookie(customer.session()))
                .andExpect(status().isOk())
                .andReturn();
        String secret = read(started, "$.clientSecret");
        return secret.substring(0, secret.indexOf("_secret_"));
    }

    /** A card order Stripe has confirmed as paid. */
    private String paidCardOrder(Customer customer) throws Exception {
        String order = placeOrder(customer, "STRIPE");
        String intent = startCardPayment(customer, order);
        stripe.setStatus(intent, "succeeded");
        stripeWebhook("""
                {"id": "evt_test_%s", "object": "event", "type": "payment_intent.succeeded", "created": 1763020800,
                 "livemode": false,
                 "data": {"object": {"id": "%s", "object": "payment_intent", "amount": %d, "amount_received": %d,
                   "currency": "eur", "status": "succeeded", "payment_method_types": ["card"]}}}
                """.formatted(UUID.randomUUID(), intent, ONE_ANIMAL_TOTAL, ONE_ANIMAL_TOTAL))
                .andExpect(status().isNoContent());
        detail(customer, order).andExpect(jsonPath("$.paymentStatus").value("PAID"));
        return order;
    }

    /** A PayPal order the customer approved and the shop captured. */
    private String paidPayPalOrder(Customer customer) throws Exception {
        String order = placeOrder(customer, "PAYPAL");
        String paypalOrder = read(mvc.perform(json(post("/api/v1/payments/paypal/orders"),
                                "{\"orderNumber\": \"%s\"}".formatted(order)).cookie(customer.session()))
                        .andExpect(status().isOk())
                        .andReturn(),
                "$.paypalOrderId");
        paypal.approve(paypalOrder);
        mvc.perform(json(post("/api/v1/payments/paypal/captures"), "{\"orderNumber\": \"%s\"}".formatted(order))
                        .cookie(customer.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
        return order;
    }

    private static String refundBody(long amountCents, long expectedRefundableCents, String extraJson) {
        return "{\"amountCents\": %d, \"expectedRefundableCents\": %d%s}"
                .formatted(amountCents, expectedRefundableCents, extraJson);
    }

    private ResultActions refund(String order, String key, String body) throws Exception {
        return mvc.perform(json(post(ADMIN_ORDERS + "/" + order + "/refunds"), body)
                .header("Idempotency-Key", key)
                .cookie(admin));
    }

    private ResultActions adminView(String order) throws Exception {
        return mvc.perform(get(ADMIN_ORDERS + "/" + order).cookie(admin)).andExpect(status().isOk());
    }

    private ResultActions detail(Customer customer, String order) throws Exception {
        return mvc.perform(get("/api/v1/account/orders/" + order).cookie(customer.session()))
                .andExpect(status().isOk());
    }

    private ResultActions stripeWebhook(String payload) throws Exception {
        return mvc.perform(post("/api/v1/webhooks/stripe")
                .contentType("application/json")
                .content(payload)
                .header("Stripe-Signature", Webhook.Signature.generateSignatureHeader(payload, WEBHOOK_SECRET))
                .with(uniqueClientIp()));
    }

    private ResultActions paypalWebhook(String payload) throws Exception {
        return mvc.perform(post("/api/v1/webhooks/paypal")
                .contentType("application/json")
                .content(payload)
                .header("PAYPAL-AUTH-ALGO", "SHA256withRSA")
                .header("PAYPAL-CERT-URL", "https://api.sandbox.paypal.com/v1/notifications/certs/CERT-test")
                .header("PAYPAL-TRANSMISSION-ID", UUID.randomUUID().toString())
                .header("PAYPAL-TRANSMISSION-SIG", FakePayPalGateway.GENUINE_SIGNATURE)
                .header("PAYPAL-TRANSMISSION-TIME", "2026-11-17T10:00:00Z")
                .with(uniqueClientIp()));
    }

    /** Stripe's report on the charge, with everything refunded so far. */
    private static String chargeRefundedEvent(String intentId, long amountRefunded) {
        return """
                {"id": "evt_test_%s", "object": "event", "type": "charge.refunded", "created": 1763020800,
                 "livemode": false,
                 "data": {"object": {"id": "ch_test_1", "object": "charge", "payment_intent": "%s", "amount": 1290,
                   "amount_refunded": %d, "currency": "eur"}}}
                """.formatted(UUID.randomUUID(), intentId, amountRefunded);
    }

    /** Stripe's report on one refund (shortened), with a name that must not be kept. */
    private static String refundEvent(
            String type, String refundId, String intentId, long amount, String status, String failureReason) {
        return """
                {"id": "evt_test_%s", "object": "event", "type": "%s", "created": 1763020800, "livemode": false,
                 "data": {"object": {"id": "%s", "object": "refund", "payment_intent": "%s", "amount": %d,
                   "currency": "eur", "status": "%s"%s,
                   "metadata": {"customer": "Jane Doe"}}}}
                """.formatted(UUID.randomUUID(), type, refundId, intentId, amount, status,
                failureReason == null ? "" : ", \"failure_reason\": \"%s\"".formatted(failureReason));
    }

    /** PayPal's report on one refund, with everything refunded so far, linked to its capture. */
    private static String paypalRefundEvent(String refundId, String status, String captureId, String totalRefunded) {
        return """
                {"id": "WH-TEST-%s", "event_version": "1.0", "create_time": "2026-11-17T10:00:00Z",
                 "resource_type": "refund", "event_type": "PAYMENT.CAPTURE.REFUNDED",
                 "resource": {"id": "%s", "status": "%s", "amount": {"currency_code": "EUR", "value": "5.00"},
                   "seller_payable_breakdown": {"total_refunded_amount": {"currency_code": "EUR", "value": "%s"}},
                   "links": [{"rel": "up", "href": "https://api.sandbox.paypal.com/v2/payments/captures/%s"}]}}
                """.formatted(UUID.randomUUID(), refundId, status, totalRefunded, captureId);
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private String intentOf(String order) {
        return jdbc.queryForObject("""
                SELECT p.provider_reference FROM payments p JOIN orders o ON o.id = p.order_id
                WHERE o.order_number = ?
                """, String.class, order);
    }

    private Map<String, Object> paymentRow(String order) {
        return jdbc.queryForMap("""
                SELECT p.status, p.refunded_cents, p.capture_reference
                FROM payments p JOIN orders o ON o.id = p.order_id WHERE o.order_number = ?
                """, order);
    }

    private int refundRows(String order) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM payment_refunds r JOIN payments p ON p.id = r.payment_id
                JOIN orders o ON o.id = p.order_id WHERE o.order_number = ?
                """, Integer.class, order);
    }

    private List<String> auditActions(String order) {
        return jdbc.queryForList("""
                        SELECT action FROM audit_log
                        WHERE entity_id = (SELECT id FROM orders WHERE order_number = ?) ORDER BY created_at, id
                        """, String.class, order);
    }

    private <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
