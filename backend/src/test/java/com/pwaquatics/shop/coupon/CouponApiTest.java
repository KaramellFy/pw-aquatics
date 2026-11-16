package com.pwaquatics.shop.coupon;

import static com.pwaquatics.shop.support.AuthFixture.json;
import static com.pwaquatics.shop.support.AuthFixture.uniqueEmail;
import static com.pwaquatics.shop.support.TestRequests.uniqueClientIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pwaquatics.shop.customer.AddressApiTest;
import com.pwaquatics.shop.support.AuthFixture;
import com.pwaquatics.shop.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
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
 * Coupons in the cart and the checkout against a real PostgreSQL, including a limited coupon
 * redeemed by two customers at the same moment.
 */
@IntegrationTest
@Sql(scripts = "/coupon/coupon-test-data.sql", executionPhase = ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = "/coupon/coupon-test-cleanup.sql", executionPhase = ExecutionPhase.AFTER_TEST_CLASS)
class CouponApiTest {

    private static final String ALPHA = "88888888-0000-0000-0000-000000000201"; // 10.00, live
    private static final String SALE = "88888888-0000-0000-0000-000000000202"; // 10.00, reduced from 15.00
    private static final String METHOD = "TEST_COUPON_LIVE"; // 9.90, free from 100.00
    private static final String LAST_COUPON = "88888888-0000-0000-0000-000000000405";
    private static final String ONCE_COUPON = "88888888-0000-0000-0000-000000000404";

    @Autowired MockMvc mvc;
    @Autowired AuthFixture auth;
    @Autowired JdbcTemplate jdbc;

    // ------------------------------------------------------------------ cart

    @Test
    void aGuestEntersACodeAndTheCartShowsTheDiscount() throws Exception {
        Cookie cart = guestCartWith(ALPHA, 3);

        apply(cart, "  t8-ten ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupon.code").value("T8-TEN"))
                .andExpect(jsonPath("$.coupon.type").value("PERCENTAGE"))
                .andExpect(jsonPath("$.coupon.percentage").value(1000))
                .andExpect(jsonPath("$.coupon.applied").value(true))
                .andExpect(jsonPath("$.coupon.problem").value(nullValue()))
                .andExpect(jsonPath("$.coupon.saving.amount").value(300))
                .andExpect(jsonPath("$.totals.subtotal.amount").value(3000))
                .andExpect(jsonPath("$.totals.discount.amount").value(300))
                .andExpect(jsonPath("$.totals.total.amount").value(2700))
                // 27.00 at 19 % contains 4.31
                .andExpect(jsonPath("$.totals.includedTax.amount").value(431));

        mvc.perform(get("/api/v1/cart").cookie(cart))
                .andExpect(jsonPath("$.coupon.code").value("T8-TEN"))
                .andExpect(jsonPath("$.totals.total.amount").value(2700));

        mvc.perform(delete("/api/v1/cart/coupon").cookie(cart).with(csrf()).with(uniqueClientIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupon").value(nullValue()))
                .andExpect(jsonPath("$.totals.discount.amount").value(0))
                .andExpect(jsonPath("$.totals.total.amount").value(3000));
    }

    @Test
    void codesThatCannotBeUsedAreRejectedAndNotAttached() throws Exception {
        Cookie cart = guestCartWith(ALPHA, 1);

        apply(cart, "NOPE-123").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COUPON_INVALID"));
        apply(cart, "!!").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COUPON_INVALID"));
        apply(cart, "T8-OLD").andExpect(jsonPath("$.code").value("COUPON_EXPIRED"));
        // Not started yet and switched off look the same as an unknown code.
        apply(cart, "T8-LATER").andExpect(jsonPath("$.code").value("COUPON_INVALID"));
        apply(cart, "T8-OFF").andExpect(jsonPath("$.code").value("COUPON_INVALID"));
        // 10.00 is below the 30.00 minimum.
        apply(cart, "T8-FIVE").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COUPON_MIN_ORDER_NOT_MET"));
        apply(cart, "").andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/cart").cookie(cart)).andExpect(jsonPath("$.coupon").value(nullValue()));

        // Only reduced products: a percentage does not apply, free shipping does.
        Cookie saleOnly = guestCartWith(SALE, 1);
        apply(saleOnly, "T8-TEN").andExpect(jsonPath("$.code").value("COUPON_NOT_APPLICABLE"));
        apply(saleOnly, "T8-SHIP").andExpect(status().isOk())
                .andExpect(jsonPath("$.coupon.applied").value(true))
                .andExpect(jsonPath("$.totals.discount.amount").value(0));

        // An empty cart takes no code.
        mvc.perform(json(put("/api/v1/cart/coupon"), "{\"code\":\"T8-TEN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_EMPTY"));
    }

    @Test
    void aCouponThatStopsApplyingStaysInTheCartWithTheReason() throws Exception {
        Cookie cart = guestCartWith(ALPHA, 3);
        add(cart, SALE, 1);
        apply(cart, "T8-FIVE")
                .andExpect(jsonPath("$.coupon.applied").value(true))
                .andExpect(jsonPath("$.coupon.minOrder.amount").value(3000))
                .andExpect(jsonPath("$.totals.discount.amount").value(500));

        String alphaLine = JsonPath.read(
                mvc.perform(get("/api/v1/cart").cookie(cart)).andReturn().getResponse().getContentAsString(),
                "$.items[0].id");
        mvc.perform(json(patch("/api/v1/cart/items/" + alphaLine), "{\"quantity\":1}").cookie(cart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupon.code").value("T8-FIVE"))
                .andExpect(jsonPath("$.coupon.applied").value(false))
                .andExpect(jsonPath("$.coupon.problem").value("COUPON_MIN_ORDER_NOT_MET"))
                .andExpect(jsonPath("$.coupon.saving.amount").value(0))
                .andExpect(jsonPath("$.totals.discount.amount").value(0))
                .andExpect(jsonPath("$.totals.total.amount").value(2000));
    }

    @Test
    void aCodeEnteredAsAGuestComesAlongWhenSigningIn() throws Exception {
        String email = uniqueEmail();
        auth.register(email);
        auth.verify(email);
        Cookie cart = guestCartWith(ALPHA, 2);
        apply(cart, "T8-TEN").andExpect(status().isOk());

        Cookie session = auth.login(email, AuthFixture.PASSWORD);
        mvc.perform(get("/api/v1/cart").cookie(session, cart))
                .andExpect(jsonPath("$.coupon.code").value("T8-TEN"))
                .andExpect(jsonPath("$.totals.total.amount").value(1800));
    }

    // ------------------------------------------------------------------ checkout

    @Test
    void theOrderKeepsTheDiscountAndTheCouponIsCounted() throws Exception {
        Customer customer = customerWith(ALPHA, 3);
        apply(customer.session(), "T8-TEN").andExpect(status().isOk());

        quote(customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.subtotal.amount").value(3000))
                .andExpect(jsonPath("$.totals.discount.amount").value(300))
                .andExpect(jsonPath("$.totals.shipping.amount").value(990))
                .andExpect(jsonPath("$.totals.total.amount").value(3690))
                .andExpect(jsonPath("$.coupon.code").value("T8-TEN"))
                .andExpect(jsonPath("$.coupon.applied").value(true));

        // The total without the discount is not what the server calculates.
        place(customer, 3990).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRICE_CHANGED"));

        String orderNumber = read(place(customer, 3690).andExpect(status().isCreated()).andReturn(), "$.orderNumber");
        mvc.perform(get("/api/v1/account/orders/" + orderNumber).cookie(customer.session()))
                .andExpect(jsonPath("$.couponCode").value("T8-TEN"))
                .andExpect(jsonPath("$.totals.discount.amount").value(300))
                .andExpect(jsonPath("$.totals.total.amount").value(3690))
                // The line shows its price before the order discount.
                .andExpect(jsonPath("$.items[0].lineTotal.amount").value(3000));

        assertThat(jdbc.queryForObject("""
                SELECT i.discount_cents FROM order_items i JOIN orders o ON o.id = i.order_id WHERE o.order_number = ?
                """, Long.class, orderNumber)).isEqualTo(300);
        assertThat(jdbc.queryForObject("""
                SELECT r.discount_cents FROM coupon_redemptions r JOIN orders o ON o.id = r.order_id
                WHERE o.order_number = ?
                """, Long.class, orderNumber)).isEqualTo(300);
        // The coupon left the cart with the order.
        mvc.perform(get("/api/v1/cart").cookie(customer.session())).andExpect(jsonPath("$.coupon").value(nullValue()));
        assertThat(auth.emailsTo(customer.email()).getLast().getPayload().body()).contains("T8-TEN");
    }

    @Test
    void aFreeShippingCouponWaivesTheShippingPrice() throws Exception {
        Customer customer = customerWith(ALPHA, 2);
        apply(customer.session(), "T8-SHIP").andExpect(status().isOk());

        mvc.perform(get("/api/v1/checkout/shipping-methods").param("country", "DE").cookie(customer.session()))
                .andExpect(jsonPath("$.methods[0].price.amount").value(0));
        quote(customer)
                .andExpect(jsonPath("$.totals.shipping.amount").value(0))
                .andExpect(jsonPath("$.totals.discount.amount").value(0))
                .andExpect(jsonPath("$.totals.total.amount").value(2000))
                .andExpect(jsonPath("$.shippingMethod.price.amount").value(0))
                .andExpect(jsonPath("$.coupon.saving.amount").value(990));

        String orderNumber = read(place(customer, 2000).andExpect(status().isCreated()).andReturn(), "$.orderNumber");
        assertThat(jdbc.queryForObject("""
                SELECT r.discount_cents FROM coupon_redemptions r JOIN orders o ON o.id = r.order_id
                WHERE o.order_number = ?
                """, Long.class, orderNumber)).isEqualTo(990);
        mvc.perform(get("/api/v1/account/orders/" + orderNumber).cookie(customer.session()))
                .andExpect(jsonPath("$.couponCode").value("T8-SHIP"))
                .andExpect(jsonPath("$.totals.shipping.amount").value(0));
    }

    @Test
    void aCustomerUsesAOncePerCustomerCodeOnceUntilTheOrderIsCancelled() throws Exception {
        Customer customer = customerWith(ALPHA, 1);
        apply(customer.session(), "T8-ONCE").andExpect(status().isOk());
        String orderNumber = read(place(customer, 900 + 990).andExpect(status().isCreated()).andReturn(),
                "$.orderNumber");
        assertThat(timesRedeemed(ONCE_COUPON)).isEqualTo(1);

        add(customer.session(), ALPHA, 1);
        apply(customer.session(), "T8-ONCE").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COUPON_ALREADY_USED"));

        // Cancelling the order gives the coupon back.
        mvc.perform(post("/api/v1/account/orders/" + orderNumber + "/cancel")
                        .cookie(customer.session()).with(csrf()).with(uniqueClientIp()))
                .andExpect(status().isOk());
        assertThat(timesRedeemed(ONCE_COUPON)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT released_at IS NOT NULL FROM coupon_redemptions r JOIN orders o ON o.id = r.order_id
                WHERE o.order_number = ?
                """, Boolean.class, orderNumber)).isTrue();
        apply(customer.session(), "T8-ONCE").andExpect(status().isOk());
    }

    @Test
    void aCouponWithOneUseLeftIsRedeemedOnlyOnceWhenTwoCustomersOrderAtTheSameMoment() throws Exception {
        Customer first = customerWith(ALPHA, 1);
        Customer second = customerWith(ALPHA, 1);
        apply(first.session(), "T8-LAST").andExpect(status().isOk());
        apply(second.session(), "T8-LAST").andExpect(status().isOk());

        CyclicBarrier start = new CyclicBarrier(2);
        List<Integer> statuses;
        List<String> bodies;
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<MvcResult>> results = List.of(first, second).stream()
                    .map(customer -> pool.submit(() -> {
                        start.await(10, TimeUnit.SECONDS);
                        return place(customer, 900 + 990).andReturn();
                    }))
                    .toList();
            MvcResult a = results.get(0).get(60, TimeUnit.SECONDS);
            MvcResult b = results.get(1).get(60, TimeUnit.SECONDS);
            statuses = List.of(a.getResponse().getStatus(), b.getResponse().getStatus());
            bodies = List.of(a.getResponse().getContentAsString(), b.getResponse().getContentAsString());
        }

        assertThat(statuses).containsExactlyInAnyOrder(201, 422);
        assertThat(bodies).anyMatch(body -> body.contains("COUPON_USAGE_LIMIT_REACHED"));
        assertThat(timesRedeemed(LAST_COUPON)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM coupon_redemptions WHERE coupon_id = ?::uuid", Integer.class, LAST_COUPON))
                .isEqualTo(1);

        // The customer who lost the race now sees why, and can still order without the code.
        Customer loser = statuses.get(0) == 422 ? first : second;
        quote(loser)
                .andExpect(jsonPath("$.coupon.applied").value(false))
                .andExpect(jsonPath("$.coupon.problem").value("COUPON_USAGE_LIMIT_REACHED"))
                .andExpect(jsonPath("$.totals.total.amount").value(1000 + 990));
        place(loser, 1000 + 990).andExpect(status().isCreated());
        assertThat(timesRedeemed(LAST_COUPON)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    private record Customer(String email, Cookie session, String addressId) {}

    private Customer customerWith(String variantId, int quantity) throws Exception {
        String email = uniqueEmail();
        Cookie session = auth.registeredSession(email);
        MvcResult address = mvc.perform(json(post("/api/v1/account/addresses"), AddressApiTest.ADDRESS).cookie(session))
                .andExpect(status().isCreated())
                .andReturn();
        add(session, variantId, quantity);
        return new Customer(email, session, read(address, "$.id"));
    }

    private Cookie guestCartWith(String variantId, int quantity) throws Exception {
        MvcResult result = mvc.perform(json(post("/api/v1/cart/items"),
                        "{\"variantId\":\"%s\",\"quantity\":%d}".formatted(variantId, quantity)))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("CART_TOKEN");
    }

    private void add(Cookie cookie, String variantId, int quantity) throws Exception {
        mvc.perform(json(post("/api/v1/cart/items"),
                        "{\"variantId\":\"%s\",\"quantity\":%d}".formatted(variantId, quantity)).cookie(cookie))
                .andExpect(status().isOk());
    }

    private ResultActions apply(Cookie cookie, String code) throws Exception {
        return mvc.perform(json(put("/api/v1/cart/coupon"), "{\"code\":\"%s\"}".formatted(code)).cookie(cookie));
    }

    private ResultActions quote(Customer customer) throws Exception {
        return mvc.perform(json(post("/api/v1/checkout/quote"), """
                        {"shippingAddressId": "%s", "shippingMethodCode": "%s"}
                        """.formatted(customer.addressId(), METHOD)).cookie(customer.session()));
    }

    private ResultActions place(Customer customer, long expectedTotal) throws Exception {
        return mvc.perform(json(post("/api/v1/checkout/orders"), """
                        {"shippingAddressId": "%s", "shippingMethodCode": "%s", "paymentMethod": "BANK_TRANSFER",
                         "acceptedTerms": true, "acceptedLiveAnimalConditions": true,
                         "expectedTotal": {"amount": %d, "currency": "EUR"}}
                        """.formatted(customer.addressId(), METHOD, expectedTotal))
                .cookie(customer.session())
                .header("Idempotency-Key", UUID.randomUUID().toString()));
    }

    private int timesRedeemed(String couponId) {
        return jdbc.queryForObject("SELECT times_redeemed FROM coupons WHERE id = ?::uuid", Integer.class, couponId);
    }

    private static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
