package com.pwaquatics.shop.payment;

import static com.pwaquatics.shop.support.AuthFixture.json;
import static com.pwaquatics.shop.support.AuthFixture.uniqueEmail;
import static com.pwaquatics.shop.support.TestRequests.uniqueClientIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pwaquatics.shop.customer.AddressApiTest;
import com.pwaquatics.shop.notification.domain.OutboxMessage;
import com.pwaquatics.shop.order.application.OrderExpiry;
import com.pwaquatics.shop.support.AuthFixture;
import com.pwaquatics.shop.support.FakePayPalGateway;
import com.pwaquatics.shop.support.IntegrationTest;
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
 * Payments with PayPal end to end, with PayPal's API replaced by {@link FakePayPalGateway}. What
 * must never break: the money is taken by our server for the stored total and only after the
 * customer approved; an order is paid only when PayPal says so to our server (the answer to our
 * capture, or a genuine webhook), exactly once; and a cancelled order is never captured.
 */
@IntegrationTest
@Sql(scripts = "/order/order-test-data.sql", executionPhase = ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = "/order/order-test-cleanup.sql", executionPhase = ExecutionPhase.AFTER_TEST_CLASS)
class PayPalPaymentApiTest {

    private static final String ALPHA_SINGLE = "22222222-0000-0000-0000-000000000201"; // 3.00, live animal
    private static final String ALPHA_PRODUCT = "22222222-0000-0000-0000-000000000101";
    private static final String LIVE = "TEST_LIVE"; // 9.90 shipping
    private static final long ONE_ANIMAL_TOTAL = 1290;

    @Autowired MockMvc mvc;
    @Autowired AuthFixture auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired FakePayPalGateway paypal;
    @Autowired OrderExpiry expiry;

    @AfterEach
    void paypalIsBack() {
        paypal.reset();
    }

    // ------------------------------------------------------------------ access

    @Test
    void payPalPaymentsNeedASignedInCustomerACsrfTokenAndTheirOwnPayPalOrder() throws Exception {
        mvc.perform(json(post("/api/v1/payments/paypal/orders"), "{\"orderNumber\":\"PW-2026-000001\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/v1/payments/paypal/captures"), "{\"orderNumber\":\"PW-2026-000001\"}"))
                .andExpect(status().isUnauthorized());

        Customer customer = customer();
        String order = placeOrder(customer, "PAYPAL");
        mvc.perform(post("/api/v1/payments/paypal/orders").cookie(customer.session())
                        .contentType("application/json")
                        .content("{\"orderNumber\":\"%s\"}".formatted(order)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        assertThat(paypal.orderOf(orderId(order))).isNull();

        // Another customer's order does not exist for them; a malformed number is refused.
        Customer other = customer();
        start(other, order).andExpect(status().isNotFound());
        capture(other, order).andExpect(status().isNotFound());
        start(customer, "PW-1").andExpect(status().isBadRequest());

        // An order placed for another payment method.
        String cardOrder = placeOrder(customer, "STRIPE");
        start(customer, cardOrder)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_METHOD_UNAVAILABLE"));
        // Capturing before the payment was started.
        capture(customer, order)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_STARTED"));
    }

    @Test
    void webhooksNeedNoSessionButMustBeConfirmedByPayPal() throws Exception {
        Customer customer = customer();
        String order = placeOrder(customer, "PAYPAL");
        String paypalOrder = startPayment(customer, order);
        paypal.approve(paypalOrder);
        String event = approvedEvent(paypalOrder);
        int capturesBefore = paypal.captureCalls();

        // No PayPal headers, or a signature PayPal does not confirm.
        mvc.perform(post("/api/v1/webhooks/paypal").contentType("application/json").content(event)
                        .with(uniqueClientIp()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
        mvc.perform(withPayPalHeaders(post("/api/v1/webhooks/paypal"), "forged").content(event))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
        // Confirmed, but not an event; not even JSON; two JSON values in one body.
        webhook("{\"hello\": \"world\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEBHOOK_INVALID"));
        webhook("not json").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WEBHOOK_INVALID"));
        webhook(event + " {}").andExpect(status().isBadRequest());

        detail(customer, order).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        assertThat(paypal.captureCalls()).isEqualTo(capturesBefore);

        // A confirmed event is accepted without session or CSRF token.
        webhook(event).andExpect(status().isNoContent());
        detail(customer, order).andExpect(jsonPath("$.status").value("PAID"));
    }

    // ------------------------------------------------------------------ paying

    @Test
    void ourServerTakesTheApprovedPaymentForTheStoredTotalExactlyOnce() throws Exception {
        Customer customer = customer();
        add(customer.session(), ALPHA_SINGLE, 1);
        int capturesBefore = paypal.captureCalls();

        // Reserved for about an hour, like a card payment; the email links to the payment page.
        Instant before = Instant.now();
        MvcResult placed = place(customer, "PAYPAL", ONE_ANIMAL_TOTAL)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentMethod").value("PAYPAL"))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andReturn();
        String order = read(placed, "$.orderNumber");
        Instant due = Instant.parse(read(placed, "$.paymentDueAt"));
        assertThat(Duration.between(before, due)).isBetween(Duration.ofMinutes(59), Duration.ofMinutes(61));
        assertThat(auth.emailsTo(customer.email()).getLast().getPayload().body())
                .contains("Payment with PayPal", "/checkout/pay/" + order);
        detail(customer, order)
                .andExpect(jsonPath("$.payableOnline").value(true))
                .andExpect(jsonPath("$.paymentInstructions").doesNotExist());

        // The PayPal order is created for the stored total; amounts in the request are ignored.
        MvcResult started = mvc.perform(json(post("/api/v1/payments/paypal/orders"),
                        "{\"orderNumber\": \"%s\", \"amount\": {\"amount\": 1, \"currency\": \"EUR\"}}".formatted(order))
                        .cookie(customer.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(order))
                .andExpect(jsonPath("$.amount.amount").value(ONE_ANIMAL_TOTAL))
                .andExpect(jsonPath("$.clientId").value("test-paypal-client-id"))
                .andExpect(jsonPath("$.alreadyPaid").value(false))
                .andReturn();
        String paypalOrder = read(started, "$.paypalOrderId");
        assertThat(paypalOrder).isEqualTo(paypal.orderOf(orderId(order)));
        // A reload returns the same PayPal order.
        assertThat(startPayment(customer, order)).isEqualTo(paypalOrder);

        // The browser cannot take the money before the customer approved in PayPal's window.
        capture(customer, order).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DECLINED"));
        detail(customer, order).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        assertThat(paymentRow(order)).containsEntry("status", "FAILED").containsEntry("failure_code", "ORDER_NOT_APPROVED");

        paypal.approve(paypalOrder);
        capture(customer, order).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
        detail(customer, order)
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.payableOnline").value(false));
        assertThat(paymentRow(order))
                .containsEntry("status", "SUCCEEDED")
                .containsEntry("amount_cents", ONE_ANIMAL_TOTAL)
                .containsEntry("method_detail", "paypal");
        assertThat((String) paymentRow(order).get("capture_reference")).startsWith("TESTCAPTURE");
        assertThat(paymentReceivedEmails(customer)).hasSize(1);
        assertThat(paymentReceivedEmails(customer).getFirst().getPayload().body()).contains("€12.90");

        // Clicking twice, PayPal's webhooks afterwards: nothing changes, no second email.
        capture(customer, order).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_ALREADY_PAID"));
        start(customer, order).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_ALREADY_PAID"));
        String capture = (String) paymentRow(order).get("capture_reference");
        webhook(captureEvent("PAYMENT.CAPTURE.COMPLETED", capture, paypalOrder, "12.90", "COMPLETED"))
                .andExpect(status().isNoContent());
        webhook(approvedEvent(paypalOrder)).andExpect(status().isNoContent());
        assertThat(paymentReceivedEmails(customer)).hasSize(1);
        assertThat(paypal.captureCalls()).isEqualTo(capturesBefore + 2);
    }

    @Test
    void aDeclinedFundingSourceLetsTheCustomerChooseAnother() throws Exception {
        Customer customer = customer();
        String order = placeOrder(customer, "PAYPAL");
        String paypalOrder = startPayment(customer, order);
        paypal.approve(paypalOrder);

        paypal.refuseNextCapture("INSTRUMENT_DECLINED");
        capture(customer, order).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DECLINED"));
        assertThat(paymentRow(order))
                .containsEntry("status", "FAILED")
                .containsEntry("failure_code", "INSTRUMENT_DECLINED");
        detail(customer, order)
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.payableOnline").value(true));

        capture(customer, order).andExpect(jsonPath("$.status").value("PAID"));
        detail(customer, order).andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void aPaymentPayPalStillChecksIsPaidWhenItsWebhookArrives() throws Exception {
        Customer customer = customer();
        String order = placeOrder(customer, "PAYPAL");
        String paypalOrder = startPayment(customer, order);
        paypal.approve(paypalOrder);
        paypal.setCaptureStatus("PENDING");

        capture(customer, order).andExpect(jsonPath("$.status").value("PENDING"));
        detail(customer, order).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        assertThat(paymentRow(order)).containsEntry("status", "PENDING");
        // Nothing more to pay, and the order can no longer be cancelled or expire meanwhile.
        start(customer, order).andExpect(jsonPath("$.alreadyPaid").value(true))
                .andExpect(jsonPath("$.paypalOrderId").doesNotExist());
        cancel(customer, order).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
        jdbc.update("UPDATE orders SET payment_due_at = now() - interval '1 minute' WHERE order_number = ?", order);
        assertThat(expiry.expire(orderId(order))).isFalse();

        String capture = (String) paymentRow(order).get("capture_reference");
        webhook(captureEvent("PAYMENT.CAPTURE.COMPLETED", capture, paypalOrder, "12.90", "COMPLETED"))
                .andExpect(status().isNoContent());
        detail(customer, order).andExpect(jsonPath("$.status").value("PAID"));
        assertThat(paymentReceivedEmails(customer)).hasSize(1);
    }

    @Test
    void anApprovalTheBrowserNeverReportedIsCapturedFromPayPalsWebhook() throws Exception {
        Customer customer = customer();
        String order = placeOrder(customer, "PAYPAL");
        String paypalOrder = startPayment(customer, order);
        paypal.approve(paypalOrder);
        int capturesBefore = paypal.captureCalls();

        String event = approvedEvent(paypalOrder);
        webhook(event).andExpect(status().isNoContent());
        detail(customer, order).andExpect(jsonPath("$.status").value("PAID"));
        assertThat(paymentReceivedEmails(customer)).hasSize(1);

        // Stored once, reduced to ids and statuses: nothing about the payer.
        webhook(event).andExpect(status().isNoContent());
        Map<String, Object> stored = jdbc.queryForMap(
                "SELECT event_type, payload::text AS payload, processed_at FROM payment_webhook_events "
                        + "WHERE provider = 'PAYPAL' AND event_id = ?",
                JsonPath.<String>read(event, "$.id"));
        assertThat(stored.get("event_type")).isEqualTo("CHECKOUT.ORDER.APPROVED");
        assertThat(stored.get("processed_at")).isNotNull();
        assertThat((String) stored.get("payload")).contains(paypalOrder).doesNotContain("jane", "Jane", "payer");
        assertThat(paypal.captureCalls()).isEqualTo(capturesBefore + 1);
    }

    @Test
    void aWebhookForADifferentAmountDoesNotMarkTheOrderAsPaid() throws Exception {
        Customer customer = customer();
        String order = placeOrder(customer, "PAYPAL");
        String paypalOrder = startPayment(customer, order);
        String capture = paypal.takeMoneyBehindTheShopsBack(paypalOrder, "COMPLETED");

        String event = captureEvent("PAYMENT.CAPTURE.COMPLETED", capture, paypalOrder, "0.01", "COMPLETED");
        webhook(event).andExpect(status().isNoContent());
        detail(customer, order).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        assertThat(jdbc.queryForObject("SELECT error FROM payment_webhook_events WHERE event_id = ?", String.class,
                JsonPath.<String>read(event, "$.id"))).isEqualTo("amount mismatch");
    }

    // ------------------------------------------------------------------ cancelling, expiry, refunds

    @Test
    void unpaidPayPalOrdersExpireButNeverWhilePayPalHasTheMoney() throws Exception {
        int stockBefore = stock(ALPHA_PRODUCT);
        Customer started = customer();
        String startedOrder = placeOrder(started, "PAYPAL");
        String startedPayPal = startPayment(started, startedOrder);
        Customer paidLate = customer();
        String paidLateOrder = placeOrder(paidLate, "PAYPAL");
        String paidLatePayPal = startPayment(paidLate, paidLateOrder);
        // PayPal took the money, but the answer to our capture got lost.
        String capture = paypal.takeMoneyBehindTheShopsBack(paidLatePayPal, "COMPLETED");

        for (String order : List.of(startedOrder, paidLateOrder)) {
            jdbc.update("UPDATE orders SET payment_due_at = now() - interval '1 minute' WHERE order_number = ?", order);
        }
        assertThat(expiry.expire(orderId(startedOrder))).isTrue();
        assertThat(expiry.expire(orderId(paidLateOrder))).isFalse();
        assertThat(stock(ALPHA_PRODUCT)).isEqualTo(stockBefore - 1);

        assertThat(paymentRow(startedOrder)).containsEntry("status", "CANCELLED");
        detail(started, startedOrder)
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.payableOnline").value(false));
        assertThat(auth.emailsTo(started.email()).getLast().getPayload().body())
                .contains("PayPal payment was not completed in time", "Nothing has been taken through PayPal");

        // A cancelled order is never captured: not from the browser, not from a late approval.
        paypal.approve(startedPayPal);
        capture(started, startedOrder).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PAYABLE"));
        String approval = approvedEvent(startedPayPal);
        webhook(approval).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT error FROM payment_webhook_events WHERE event_id = ?", String.class,
                JsonPath.<String>read(approval, "$.id"))).isEqualTo("order not awaiting payment");
        detail(started, startedOrder).andExpect(jsonPath("$.status").value("CANCELLED"));

        // The order that kept its animals is paid when PayPal's webhook arrives.
        webhook(captureEvent("PAYMENT.CAPTURE.COMPLETED", capture, paidLatePayPal, "12.90", "COMPLETED"))
                .andExpect(status().isNoContent());
        detail(paidLate, paidLateOrder).andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void customersCanCancelAnUnpaidPayPalOrder() throws Exception {
        Customer customer = customer();
        String order = placeOrder(customer, "PAYPAL");
        startPayment(customer, order);

        cancel(customer, order).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(paymentRow(order)).containsEntry("status", "CANCELLED");
        start(customer, order).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_NOT_PAYABLE"));
    }

    @Test
    void refundsMadeAtPayPalAreRecordedOnTheOrder() throws Exception {
        Customer customer = customer();
        String order = placeOrder(customer, "PAYPAL");
        String paypalOrder = startPayment(customer, order);
        paypal.approve(paypalOrder);
        capture(customer, order).andExpect(jsonPath("$.status").value("PAID"));
        String capture = (String) paymentRow(order).get("capture_reference");

        webhook(refundEvent(capture, "5.00")).andExpect(status().isNoContent());
        assertThat(paymentRow(order)).containsEntry("status", "PARTIALLY_REFUNDED").containsEntry("refunded_cents", 500L);
        detail(customer, order).andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_REFUNDED"));

        webhook(refundEvent(capture, "12.90")).andExpect(status().isNoContent());
        assertThat(paymentRow(order)).containsEntry("status", "REFUNDED").containsEntry("refunded_cents", ONE_ANIMAL_TOTAL);
        detail(customer, order).andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));
    }

    @Test
    void whenPayPalCannotBeReachedTheCustomerIsAskedToTryAgain() throws Exception {
        Customer customer = customer();
        String order = placeOrder(customer, "PAYPAL");
        paypal.setDown(true);
        start(customer, order)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_PROVIDER_UNAVAILABLE"));
        // Nothing was recorded: the next attempt starts afresh.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id = ?", Integer.class, orderId(order)))
                .isZero();
        paypal.setDown(false);
        assertThat(startPayment(customer, order)).startsWith("TESTPAYPAL");
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

    private void add(Cookie session, String variantId, int quantity) throws Exception {
        mvc.perform(json(post("/api/v1/cart/items"),
                        "{\"variantId\":\"%s\",\"quantity\":%d}".formatted(variantId, quantity)).cookie(session))
                .andExpect(status().isOk());
    }

    /** One animal, to pay with the given method. */
    private String placeOrder(Customer customer, String paymentMethod) throws Exception {
        add(customer.session(), ALPHA_SINGLE, 1);
        return read(place(customer, paymentMethod, ONE_ANIMAL_TOTAL).andExpect(status().isCreated()).andReturn(),
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

    private ResultActions start(Customer customer, String orderNumber) throws Exception {
        return mvc.perform(json(post("/api/v1/payments/paypal/orders"), "{\"orderNumber\": \"%s\"}".formatted(orderNumber))
                .cookie(customer.session()));
    }

    /** @return the PayPal order id */
    private String startPayment(Customer customer, String orderNumber) throws Exception {
        return read(start(customer, orderNumber)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.alreadyPaid").value(false))
                        .andReturn(),
                "$.paypalOrderId");
    }

    private ResultActions capture(Customer customer, String orderNumber) throws Exception {
        return mvc.perform(json(post("/api/v1/payments/paypal/captures"), "{\"orderNumber\": \"%s\"}".formatted(orderNumber))
                .cookie(customer.session()));
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
        return mvc.perform(withPayPalHeaders(post("/api/v1/webhooks/paypal"), FakePayPalGateway.GENUINE_SIGNATURE)
                .content(payload));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withPayPalHeaders(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String signature) {
        return request.contentType("application/json")
                .header("PAYPAL-AUTH-ALGO", "SHA256withRSA")
                .header("PAYPAL-CERT-URL", "https://api.sandbox.paypal.com/v1/notifications/certs/CERT-test")
                .header("PAYPAL-TRANSMISSION-ID", UUID.randomUUID().toString())
                .header("PAYPAL-TRANSMISSION-SIG", signature)
                .header("PAYPAL-TRANSMISSION-TIME", "2026-11-17T10:00:00Z")
                .with(uniqueClientIp());
    }

    /** PayPal's event after the customer approved, as it sends it (shortened), with a new event id. */
    private static String approvedEvent(String paypalOrderId) {
        return """
                {"id": "WH-TEST-%s", "event_version": "1.0", "create_time": "2026-11-17T10:00:00Z",
                 "resource_type": "checkout-order", "event_type": "CHECKOUT.ORDER.APPROVED",
                 "resource": {"id": "%s", "intent": "CAPTURE", "status": "APPROVED",
                   "payer": {"name": {"given_name": "Jane", "surname": "Doe"}, "email_address": "jane@example.com"},
                   "purchase_units": [{"amount": {"currency_code": "EUR", "value": "12.90"}}]}}
                """.formatted(UUID.randomUUID(), paypalOrderId);
    }

    private static String captureEvent(String type, String captureId, String paypalOrderId, String value, String status) {
        return """
                {"id": "WH-TEST-%s", "event_version": "1.0", "create_time": "2026-11-17T10:00:00Z",
                 "resource_type": "capture", "event_type": "%s",
                 "resource": {"id": "%s", "status": "%s", "amount": {"currency_code": "EUR", "value": "%s"},
                   "supplementary_data": {"related_ids": {"order_id": "%s"}},
                   "links": [{"rel": "up", "href": "https://api.sandbox.paypal.com/v2/checkout/orders/%s"}]}}
                """.formatted(UUID.randomUUID(), type, captureId, status, value, paypalOrderId, paypalOrderId);
    }

    /** A refund as PayPal reports it: with everything refunded so far, linked to its capture. */
    private static String refundEvent(String captureId, String totalRefunded) {
        return """
                {"id": "WH-TEST-%s", "event_version": "1.0", "create_time": "2026-11-17T10:00:00Z",
                 "resource_type": "refund", "event_type": "PAYMENT.CAPTURE.REFUNDED",
                 "resource": {"id": "REFUND%s", "status": "COMPLETED", "amount": {"currency_code": "EUR", "value": "5.00"},
                   "seller_payable_breakdown": {"total_refunded_amount": {"currency_code": "EUR", "value": "%s"}},
                   "links": [{"rel": "self", "href": "https://api.sandbox.paypal.com/v2/payments/refunds/X"},
                             {"rel": "up", "href": "https://api.sandbox.paypal.com/v2/payments/captures/%s"}]}}
                """.formatted(UUID.randomUUID(), Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100000),
                totalRefunded, captureId);
    }

    private List<OutboxMessage> paymentReceivedEmails(Customer customer) {
        return auth.emailsTo(customer.email()).stream()
                .filter(message -> message.getPayload().subject().startsWith("Payment received"))
                .toList();
    }

    private Map<String, Object> paymentRow(String orderNumber) {
        return jdbc.queryForMap("""
                SELECT p.status, p.amount_cents, p.refunded_cents, p.method_detail, p.failure_code, p.capture_reference
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
