package com.pwaquatics.shop.order;

import static com.pwaquatics.shop.support.AuthFixture.json;
import static com.pwaquatics.shop.support.AuthFixture.uniqueEmail;
import static com.pwaquatics.shop.support.TestRequests.uniqueClientIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pwaquatics.shop.customer.AddressApiTest;
import com.pwaquatics.shop.notification.domain.OutboxMessage;
import com.pwaquatics.shop.order.application.OrderExpiry;
import com.pwaquatics.shop.support.AuthFixture;
import com.pwaquatics.shop.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Checkout and orders end to end against a real PostgreSQL, including the scenarios that
 * must never break: the last animal bought twice, duplicate submissions, changed prices.
 */
@IntegrationTest
@Sql(scripts = "/order/order-test-data.sql", executionPhase = ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = "/order/order-test-cleanup.sql", executionPhase = ExecutionPhase.AFTER_TEST_CLASS)
class CheckoutApiTest {

    private static final String ALPHA_SINGLE = "22222222-0000-0000-0000-000000000201"; // 3.00, live, stock 100
    private static final String ALPHA_TEN = "22222222-0000-0000-0000-000000000202"; // 25.00, pack of 10
    private static final String LAST_ONE = "22222222-0000-0000-0000-000000000203"; // 15.00, stock 1
    private static final String FOOD = "22222222-0000-0000-0000-000000000204"; // 5.00, not a live animal
    private static final String ALPHA_PRODUCT = "22222222-0000-0000-0000-000000000101";
    private static final String LAST_PRODUCT = "22222222-0000-0000-0000-000000000102";
    private static final String LIVE = "TEST_LIVE"; // 9.90, free from 100.00, DE + GB, Monday to Wednesday
    private static final String PARCEL = "TEST_PARCEL"; // 4.90, DE, no live animals
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Autowired MockMvc mvc;
    @Autowired AuthFixture auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderExpiry expiry;

    // ------------------------------------------------------------------ access

    @Test
    void checkoutAndOrdersNeedASignedInCustomerAndACsrfToken() throws Exception {
        mvc.perform(get("/api/v1/checkout/shipping-methods").param("country", "DE"))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/v1/checkout/quote"), "{}")).andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/v1/checkout/orders"), "{}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/account/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/account/orders/PW-2026-000001")).andExpect(status().isUnauthorized());

        Customer customer = customerWith(ALPHA_SINGLE, 1);
        mvc.perform(post("/api/v1/checkout/orders").cookie(customer.session())
                        .header("Idempotency-Key", key())
                        .contentType("application/json")
                        .content(orderBody(customer, LIVE, 1290, "")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        assertThat(orderCount(customer)).isZero();
    }

    // ------------------------------------------------------------------ shipping and quote

    @Test
    void shippingMethodsDependOnTheDestinationAndTheCart() throws Exception {
        Customer live = customerWith(ALPHA_SINGLE, 2);

        MvcResult result = mvc.perform(get("/api/v1/checkout/shipping-methods").param("country", "DE")
                        .cookie(live.session()))
                .andExpect(status().isOk())
                // The parcel method does not carry live animals.
                .andExpect(jsonPath("$.methods[*].code").value(contains(LIVE)))
                .andExpect(jsonPath("$.methods[0].price.amount").value(990))
                .andExpect(jsonPath("$.methods[0].freeFrom.amount").value(10000))
                .andExpect(jsonPath("$.methods[0].carrier").value("DHL"))
                .andExpect(jsonPath("$.paused").value(false))
                .andReturn();
        LocalDate dispatch = LocalDate.parse(read(result, "$.methods[0].plannedDispatchDate"));
        LocalDate today = LocalDate.now(BERLIN);
        assertThat(dispatch).isAfter(today).isBeforeOrEqualTo(today.plusDays(7));
        assertThat(dispatch.getDayOfWeek()).isIn(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY);
        assertThat(LocalDate.parse(read(result, "$.methods[0].estimatedDeliveryFrom"))).isEqualTo(dispatch.plusDays(1));
        assertThat(LocalDate.parse(read(result, "$.methods[0].estimatedDeliveryTo"))).isEqualTo(dispatch.plusDays(2));

        // A country nobody delivers to, and a malformed one.
        mvc.perform(get("/api/v1/checkout/shipping-methods").param("country", "US").cookie(live.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.methods", hasSize(0)));
        mvc.perform(get("/api/v1/checkout/shipping-methods").param("country", "germany").cookie(live.session()))
                .andExpect(status().isBadRequest());

        // From 100.00 goods value on, the live method is free.
        add(live.session(), ALPHA_TEN, 4);
        mvc.perform(get("/api/v1/checkout/shipping-methods").param("country", "DE").cookie(live.session()))
                .andExpect(jsonPath("$.methods[0].price.amount").value(0));

        // Without live animals both methods are offered.
        Customer food = customerWith(FOOD, 1);
        mvc.perform(get("/api/v1/checkout/shipping-methods").param("country", "DE").cookie(food.session()))
                .andExpect(jsonPath("$.methods[*].code").value(contains(LIVE, PARCEL)));
    }

    @Test
    void theQuoteIsCalculatedByTheServer() throws Exception {
        Customer customer = customerWith(ALPHA_SINGLE, 2);
        add(customer.session(), FOOD, 1);

        quote(customer, customer.addressId(), LIVE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].productName").value(contains("Order Alpha", "Order Food")))
                .andExpect(jsonPath("$.items[0].lineTotal.amount").value(600))
                .andExpect(jsonPath("$.totals.subtotal.amount").value(1100))
                .andExpect(jsonPath("$.totals.shipping.amount").value(990))
                .andExpect(jsonPath("$.totals.total.amount").value(2090))
                // 20.90 gross at 19 % contains 3.34 VAT (3.3369…).
                .andExpect(jsonPath("$.totals.includedTax.amount").value(334))
                .andExpect(jsonPath("$.shippingMethod.code").value(LIVE))
                .andExpect(jsonPath("$.containsLiveAnimals").value(true))
                .andExpect(jsonPath("$.paymentMethods").value(contains("BANK_TRANSFER")));

        // Live animals cannot travel with the parcel method; unknown methods do not exist.
        for (String method : new String[] {PARCEL, "NOPE"}) {
            quote(customer, customer.addressId(), method)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SHIPPING_METHOD_UNAVAILABLE"));
        }
        // Someone else's address is "not found".
        Customer stranger = customerWith(FOOD, 1);
        quote(customer, stranger.addressId(), LIVE).andExpect(status().isNotFound());
        mvc.perform(json(post("/api/v1/checkout/quote"), "{\"shippingMethodCode\":\"TEST_LIVE\"}")
                        .cookie(customer.session()))
                .andExpect(status().isBadRequest());

        Customer empty = customer();
        quote(empty, empty.addressId(), LIVE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_EMPTY"));
    }

    // ------------------------------------------------------------------ placing orders

    @Test
    void placingAnOrderStoresSnapshotsTakesStockEmptiesTheCartAndSendsTheBankDetails() throws Exception {
        Customer customer = customerWith(ALPHA_SINGLE, 2);
        add(customer.session(), ALPHA_TEN, 1);
        int stockBefore = stock(ALPHA_PRODUCT);

        // Prices in the request are ignored: only ids, codes and the expected total count.
        MvcResult placed = place(customer, key(), LIVE, 4090, """
                        , "customerNote": "  Please ring twice.  ",
                          "items": [{"variantId": "%s", "quantity": 1, "unitPrice": {"amount": 1, "currency": "EUR"}}],
                          "total": {"amount": 1, "currency": "EUR"}, "shippingPrice": 0
                        """.formatted(ALPHA_SINGLE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").value(matchesPattern("PW-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.total.amount").value(4090))
                .andExpect(jsonPath("$.paymentDueAt").isNotEmpty())
                .andExpect(jsonPath("$.paymentInstructions.type").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$.paymentInstructions.iban").value("DE00 0000 0000 0000 0000 00"))
                .andExpect(jsonPath("$.paymentInstructions.amount.amount").value(4090))
                .andReturn();
        String orderNumber = read(placed, "$.orderNumber");
        assertThat(this.<String>read(placed, "$.paymentInstructions.reference")).isEqualTo(orderNumber);

        // Two singles and a pack of ten: twelve animals left the stock, and the ledger says so.
        assertThat(stock(ALPHA_PRODUCT)).isEqualTo(stockBefore - 12);
        assertThat(jdbc.queryForMap("""
                        SELECT m.delta, m.stock_after, m.reason FROM stock_movements m
                        JOIN orders o ON o.id = m.order_id WHERE o.order_number = ?
                        """, orderNumber))
                .containsEntry("delta", -12)
                .containsEntry("stock_after", stockBefore - 12)
                .containsEntry("reason", "ORDER_PLACED");

        mvc.perform(get("/api/v1/cart").cookie(customer.session())).andExpect(jsonPath("$.items", hasSize(0)));

        assertThat(jdbc.queryForMap("""
                        SELECT customer_email::text AS email, subtotal_cents, shipping_cents, total_cents, tax_cents,
                               customer_note, terms_accepted_at IS NOT NULL AS accepted, planned_dispatch_date IS NOT NULL AS planned
                        FROM orders WHERE order_number = ?
                        """, orderNumber))
                .containsEntry("email", customer.email())
                .containsEntry("subtotal_cents", 3100L)
                .containsEntry("shipping_cents", 990L)
                .containsEntry("total_cents", 4090L)
                .containsEntry("tax_cents", 653L) // 40.90 × 19 / 119 = 6.5302…
                .containsEntry("customer_note", "Please ring twice.")
                .containsEntry("accepted", true)
                .containsEntry("planned", true);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_log WHERE action = 'ORDER_PLACED' AND entity_id = "
                                + "(SELECT id FROM orders WHERE order_number = ?)", Integer.class, orderNumber))
                .isEqualTo(1);

        OutboxMessage email = auth.emailsTo(customer.email()).getLast();
        assertThat(email.getPayload().subject()).isEqualTo("Your order " + orderNumber);
        assertThat(email.getPayload().body())
                .contains(orderNumber, "2 × Order Alpha – 1 animal", "€40.90", "DE00 0000 0000 0000 0000 00",
                        "Hauptstraße 12a", "/account/orders/" + orderNumber);

        // The order shows up in the customer's history and in full detail.
        mvc.perform(get("/api/v1/account/orders").cookie(customer.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.items[0].itemCount").value(3))
                .andExpect(jsonPath("$.items[0].productNames").value(contains("Order Alpha")))
                .andExpect(jsonPath("$.items[0].total.amount").value(4090));
        mvc.perform(get("/api/v1/account/orders/" + orderNumber).cookie(customer.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].variantName").value(contains("1 animal", "10 animals")))
                .andExpect(jsonPath("$.items[0].unitPrice.amount").value(300))
                .andExpect(jsonPath("$.items[1].lineTotal.amount").value(2500))
                .andExpect(jsonPath("$.totals.total.amount").value(4090))
                .andExpect(jsonPath("$.totals.includedTax.amount").value(653))
                .andExpect(jsonPath("$.shippingMethodName").value("Test live express"))
                .andExpect(jsonPath("$.shippingAddress.city").value("Berlin"))
                .andExpect(jsonPath("$.billingAddress.street").value("Hauptstraße"))
                .andExpect(jsonPath("$.customerNote").value("Please ring twice."))
                .andExpect(jsonPath("$.statusTimeline[*].status").value(contains("PENDING_PAYMENT")))
                .andExpect(jsonPath("$.paymentInstructions.reference").value(orderNumber))
                .andExpect(jsonPath("$.containsLiveAnimals").value(true))
                .andExpect(jsonPath("$.cancellable").value(true));
    }

    @Test
    void laterChangesToTheCatalogOrAddressBookDoNotAlterAnOrder() throws Exception {
        Customer customer = customerWith(FOOD, 2);
        String orderNumber = read(place(customer, key(), PARCEL, 1490, "").andExpect(status().isCreated()).andReturn(),
                "$.orderNumber");
        try {
            jdbc.update("UPDATE product_variants SET price_cents = 999 WHERE id = ?::uuid", FOOD);
            mvc.perform(json(put("/api/v1/account/addresses/" + customer.addressId()),
                            AddressApiTest.ADDRESS.replace("Berlin", "Hamburg")).cookie(customer.session()))
                    .andExpect(status().isOk());

            mvc.perform(get("/api/v1/account/orders/" + orderNumber).cookie(customer.session()))
                    .andExpect(jsonPath("$.items[0].unitPrice.amount").value(500))
                    .andExpect(jsonPath("$.totals.total.amount").value(1490))
                    .andExpect(jsonPath("$.shippingAddress.city").value("Berlin"))
                    .andExpect(jsonPath("$.containsLiveAnimals").value(false));
        } finally {
            jdbc.update("UPDATE product_variants SET price_cents = 500 WHERE id = ?::uuid", FOOD);
        }
    }

    @Test
    void aRepeatedSubmissionWithTheSameKeyCreatesOnlyOneOrder() throws Exception {
        Customer customer = customerWith(ALPHA_SINGLE, 1);
        int stockBefore = stock(ALPHA_PRODUCT);
        String key = key();

        String first = read(place(customer, key, LIVE, 1290, "").andExpect(status().isCreated()).andReturn(),
                "$.orderNumber");
        // The retry arrives when the cart is already empty and still gets the same order back.
        String second = read(place(customer, key, LIVE, 1290, "").andExpect(status().isCreated()).andReturn(),
                "$.orderNumber");

        assertThat(second).isEqualTo(first);
        assertThat(orderCount(customer)).isEqualTo(1);
        assertThat(stock(ALPHA_PRODUCT)).isEqualTo(stockBefore - 1);
        assertThat(auth.emailsTo(customer.email()).stream()
                        .filter(mail -> mail.getPayload().subject().startsWith("Your order")))
                .hasSize(1);

        // A new key is a new attempt, and there is nothing left to order.
        place(customer, key(), LIVE, 1290, "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_EMPTY"));
    }

    @Test
    void aChangedTotalIsCaughtBeforeAnythingIsSaved() throws Exception {
        Customer customer = customerWith(ALPHA_SINGLE, 2);
        int stockBefore = stock(ALPHA_PRODUCT);

        // A manipulated or outdated total: the server's own total is 15.90.
        place(customer, key(), LIVE, 100, "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICE_CHANGED"));

        // The price really changes between review and order.
        try {
            jdbc.update("UPDATE product_variants SET price_cents = 350 WHERE id = ?::uuid", ALPHA_SINGLE);
            place(customer, key(), LIVE, 1590, "")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PRICE_CHANGED"));
            quote(customer, customer.addressId(), LIVE).andExpect(jsonPath("$.totals.total.amount").value(1690));
        } finally {
            jdbc.update("UPDATE product_variants SET price_cents = 300 WHERE id = ?::uuid", ALPHA_SINGLE);
        }

        assertThat(orderCount(customer)).isZero();
        assertThat(stock(ALPHA_PRODUCT)).isEqualTo(stockBefore);
        mvc.perform(get("/api/v1/cart").cookie(customer.session()))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void incompleteOrInvalidOrdersAreRejected() throws Exception {
        Customer customer = customerWith(ALPHA_SINGLE, 1);
        Customer stranger = customer();
        String valid = orderBody(customer, LIVE, 1290, "");

        // No or malformed idempotency key.
        mvc.perform(json(post("/api/v1/checkout/orders"), valid).cookie(customer.session()))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/v1/checkout/orders"), valid).cookie(customer.session())
                        .header("Idempotency-Key", "short"))
                .andExpect(status().isBadRequest());
        // Terms not accepted.
        mvc.perform(json(post("/api/v1/checkout/orders"), valid.replace("\"acceptedTerms\": true", "\"acceptedTerms\": false"))
                        .cookie(customer.session()).header("Idempotency-Key", key()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        // Live animals without accepting their shipping conditions.
        mvc.perform(json(post("/api/v1/checkout/orders"),
                                valid.replace("\"acceptedLiveAnimalConditions\": true", "\"acceptedLiveAnimalConditions\": false"))
                        .cookie(customer.session()).header("Idempotency-Key", key()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LIVE_ANIMAL_CONDITIONS_REQUIRED"));
        // A payment method that is not offered (yet).
        mvc.perform(json(post("/api/v1/checkout/orders"), valid.replace("BANK_TRANSFER", "STRIPE"))
                        .cookie(customer.session()).header("Idempotency-Key", key()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_METHOD_UNAVAILABLE"));
        // Someone else's address, as shipping or as billing address.
        mvc.perform(json(post("/api/v1/checkout/orders"), valid.replace(customer.addressId(), stranger.addressId()))
                        .cookie(customer.session()).header("Idempotency-Key", key()))
                .andExpect(status().isNotFound());
        place(customer, key(), LIVE, 1290, ", \"billingAddressId\": \"%s\"".formatted(stranger.addressId()))
                .andExpect(status().isNotFound());
        // A method that does not fit the cart.
        place(customer, key(), PARCEL, 790, "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHIPPING_METHOD_UNAVAILABLE"));

        assertThat(orderCount(customer)).isZero();
        // After all that, the correct request still goes through.
        place(customer, key(), LIVE, 1290, "").andExpect(status().isCreated());
    }

    @Test
    void twoCustomersCannotBothBuyTheLastAnimal() throws Exception {
        jdbc.update("UPDATE products SET stock_units = 1 WHERE id = ?::uuid", LAST_PRODUCT);
        Customer first = customerWith(LAST_ONE, 1);
        Customer second = customerWith(LAST_ONE, 1);

        CyclicBarrier start = new CyclicBarrier(2);
        List<Integer> statuses;
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> results = List.of(first, second).stream()
                    .map(customer -> pool.submit(() -> {
                        start.await(10, TimeUnit.SECONDS);
                        return place(customer, key(), LIVE, 2490, "").andReturn().getResponse().getStatus();
                    }))
                    .toList();
            statuses = List.of(results.get(0).get(60, TimeUnit.SECONDS), results.get(1).get(60, TimeUnit.SECONDS));
        }

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(stock(LAST_PRODUCT)).isZero();
        assertThat(orderCount(first) + orderCount(second)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM stock_movements WHERE product_id = ?::uuid AND reason = 'ORDER_PLACED'",
                        Integer.class, LAST_PRODUCT))
                .isEqualTo(1);

        // The customer who came second keeps the cart and sees that the animal is gone.
        Customer loser = orderCount(first) == 0 ? first : second;
        place(loser, key(), LIVE, 2490, "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_CHANGED"));
        mvc.perform(get("/api/v1/cart").cookie(loser.session()))
                .andExpect(jsonPath("$.items[0].available").value(false))
                .andExpect(jsonPath("$.warnings[0].code").value("ITEM_UNAVAILABLE"));
    }

    // ------------------------------------------------------------------ cancelling and expiry

    @Test
    void aCustomerCanCancelAnUnpaidOrderAndTheAnimalsGoBackOnSale() throws Exception {
        Customer customer = customerWith(ALPHA_TEN, 1);
        int stockBefore = stock(ALPHA_PRODUCT);
        String orderNumber = read(place(customer, key(), LIVE, 3490, "").andExpect(status().isCreated()).andReturn(),
                "$.orderNumber");
        assertThat(stock(ALPHA_PRODUCT)).isEqualTo(stockBefore - 10);

        mvc.perform(post("/api/v1/account/orders/" + orderNumber + "/cancel").cookie(customer.session()))
                .andExpect(status().isForbidden());
        cancel(customer, orderNumber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellable").value(false))
                .andExpect(jsonPath("$.paymentInstructions").value(nullValue()))
                .andExpect(jsonPath("$.paymentDueAt").value(nullValue()))
                .andExpect(jsonPath("$.statusTimeline[*].status").value(contains("PENDING_PAYMENT", "CANCELLED")));

        assertThat(stock(ALPHA_PRODUCT)).isEqualTo(stockBefore);
        assertThat(jdbc.queryForList("""
                        SELECT m.reason FROM stock_movements m JOIN orders o ON o.id = m.order_id
                        WHERE o.order_number = ? ORDER BY m.created_at, m.id
                        """, String.class, orderNumber))
                .containsExactly("ORDER_PLACED", "ORDER_CANCELLED");
        OutboxMessage email = auth.emailsTo(customer.email()).getLast();
        assertThat(email.getPayload().subject()).isEqualTo("Order " + orderNumber + " cancelled");
        assertThat(email.getPayload().body()).contains("cancelled as requested");

        // Cancelling twice changes nothing more.
        cancel(customer, orderNumber)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
        assertThat(stock(ALPHA_PRODUCT)).isEqualTo(stockBefore);
    }

    @Test
    void ordersOfOtherCustomersAreNotFound() throws Exception {
        Customer owner = customerWith(ALPHA_SINGLE, 1);
        Customer stranger = customer();
        String orderNumber = read(place(owner, key(), LIVE, 1290, "").andExpect(status().isCreated()).andReturn(),
                "$.orderNumber");

        mvc.perform(get("/api/v1/account/orders/" + orderNumber).cookie(stranger.session()))
                .andExpect(status().isNotFound());
        cancel(stranger, orderNumber).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/account/orders").cookie(stranger.session()))
                .andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/v1/account/orders/not-an-order-number").cookie(owner.session()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/account/orders/PW-2020-999999").cookie(owner.session()))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/account/orders/" + orderNumber).cookie(owner.session()))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    @Test
    void unpaidOrdersExpireAfterThePaymentDeadlineAndReturnTheirStock() throws Exception {
        Customer late = customerWith(ALPHA_SINGLE, 3);
        Customer inTime = customerWith(ALPHA_SINGLE, 1);
        int stockBefore = stock(ALPHA_PRODUCT);
        String overdue = read(place(late, key(), LIVE, 1890, "").andExpect(status().isCreated()).andReturn(),
                "$.orderNumber");
        String open = read(place(inTime, key(), LIVE, 1290, "").andExpect(status().isCreated()).andReturn(),
                "$.orderNumber");
        jdbc.update("UPDATE orders SET payment_due_at = now() - interval '1 minute' WHERE order_number = ?", overdue);
        UUID overdueId = orderId(overdue);
        UUID openId = orderId(open);

        assertThat(expiry.overdueOrderIds(50)).contains(overdueId).doesNotContain(openId);
        assertThat(expiry.expire(overdueId)).isTrue();
        assertThat(expiry.expire(overdueId)).isFalse();
        assertThat(expiry.expire(openId)).isFalse();

        assertThat(stock(ALPHA_PRODUCT)).isEqualTo(stockBefore - 1);
        mvc.perform(get("/api/v1/account/orders/" + overdue).cookie(late.session()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellable").value(false));
        mvc.perform(get("/api/v1/account/orders/" + open).cookie(inTime.session()))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        assertThat(auth.emailsTo(late.email()).getLast().getPayload().body())
                .contains("did not receive a payment", overdue);
    }

    @Test
    void aShippingPauseBlocksOrDelaysOrdersWithLiveAnimals() throws Exception {
        Customer live = customerWith(ALPHA_SINGLE, 1);
        Customer food = customerWith(FOOD, 1);
        try {
            // Open-ended pause: live animals cannot be ordered, everything else can.
            pause("{\"active\": true, \"message\": \"Heat wave\"}");
            mvc.perform(get("/api/v1/checkout/shipping-methods").param("country", "DE").cookie(live.session()))
                    .andExpect(jsonPath("$.methods", hasSize(0)))
                    .andExpect(jsonPath("$.paused").value(true))
                    .andExpect(jsonPath("$.pauseMessage").value("Heat wave"));
            quote(live, live.addressId(), LIVE)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SHIPPING_PAUSED"));
            place(live, key(), LIVE, 1290, "")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SHIPPING_PAUSED"));
            mvc.perform(get("/api/v1/checkout/shipping-methods").param("country", "DE").cookie(food.session()))
                    .andExpect(jsonPath("$.methods[*].code").value(containsInAnyOrder(LIVE, PARCEL)))
                    .andExpect(jsonPath("$.paused").value(false));

            // Pause with an end date: orders are possible, dispatch moves behind the pause.
            LocalDate until = LocalDate.now(BERLIN).plusDays(10);
            pause("{\"active\": true, \"message\": \"Heat wave\", \"until\": \"%s\"}".formatted(until));
            MvcResult delayed = mvc.perform(get("/api/v1/checkout/shipping-methods").param("country", "DE")
                            .cookie(live.session()))
                    .andExpect(jsonPath("$.methods", hasSize(1)))
                    .andExpect(jsonPath("$.paused").value(true))
                    .andExpect(jsonPath("$.pausedUntil").value(until.toString()))
                    .andReturn();
            assertThat(LocalDate.parse(read(delayed, "$.methods[0].plannedDispatchDate"))).isAfter(until);
            place(live, key(), LIVE, 1290, "").andExpect(status().isCreated());

            // A pause that is over, switched off or unreadable has no effect.
            for (String setting : new String[] {
                "{\"active\": true, \"until\": \"2020-01-01\"}", "{\"active\": false}", "\"nonsense\""
            }) {
                pause(setting);
                mvc.perform(get("/api/v1/checkout/shipping-methods").param("country", "DE").cookie(food.session()))
                        .andExpect(jsonPath("$.paused").value(false));
            }
        } finally {
            jdbc.update("DELETE FROM shop_settings WHERE key = 'shipping.pause'");
        }
    }

    // ------------------------------------------------------------------ helpers

    private record Customer(String email, Cookie session, String addressId) {}

    /** A verified, signed-in customer with one address in Germany. */
    private Customer customer() throws Exception {
        String email = uniqueEmail();
        Cookie session = auth.registeredSession(email);
        MvcResult address = mvc.perform(json(post("/api/v1/account/addresses"), AddressApiTest.ADDRESS).cookie(session))
                .andExpect(status().isCreated())
                .andReturn();
        return new Customer(email, session, read(address, "$.id"));
    }

    private Customer customerWith(String variantId, int quantity) throws Exception {
        Customer customer = customer();
        add(customer.session(), variantId, quantity);
        return customer;
    }

    private void add(Cookie session, String variantId, int quantity) throws Exception {
        mvc.perform(json(post("/api/v1/cart/items"),
                        "{\"variantId\":\"%s\",\"quantity\":%d}".formatted(variantId, quantity)).cookie(session))
                .andExpect(status().isOk());
    }

    private ResultActions quote(Customer customer, String addressId, String method) throws Exception {
        return mvc.perform(json(post("/api/v1/checkout/quote"), """
                        {"shippingAddressId": "%s", "shippingMethodCode": "%s"}
                        """.formatted(addressId, method)).cookie(customer.session()));
    }

    private ResultActions place(Customer customer, String key, String method, long expectedTotal, String extraJson)
            throws Exception {
        return mvc.perform(json(post("/api/v1/checkout/orders"), orderBody(customer, method, expectedTotal, extraJson))
                .cookie(customer.session())
                .header("Idempotency-Key", key));
    }

    private static String orderBody(Customer customer, String method, long expectedTotal, String extraJson) {
        return """
                {"shippingAddressId": "%s", "shippingMethodCode": "%s", "paymentMethod": "BANK_TRANSFER",
                 "acceptedTerms": true, "acceptedLiveAnimalConditions": true,
                 "expectedTotal": {"amount": %d, "currency": "EUR"}%s}
                """.formatted(customer.addressId(), method, expectedTotal, extraJson);
    }

    private ResultActions cancel(Customer customer, String orderNumber) throws Exception {
        return mvc.perform(post("/api/v1/account/orders/" + orderNumber + "/cancel")
                .cookie(customer.session())
                .with(csrf())
                .with(uniqueClientIp()));
    }

    private void pause(String json) {
        jdbc.update("""
                INSERT INTO shop_settings (key, value) VALUES ('shipping.pause', ?::jsonb)
                ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value
                """, json);
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private int stock(String productId) {
        return jdbc.queryForObject("SELECT stock_units FROM products WHERE id = ?::uuid", Integer.class, productId);
    }

    private int orderCount(Customer customer) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM orders WHERE customer_email = ?::citext", Integer.class, customer.email());
    }

    private UUID orderId(String orderNumber) {
        return jdbc.queryForObject("SELECT id FROM orders WHERE order_number = ?", UUID.class, orderNumber);
    }

    private <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
