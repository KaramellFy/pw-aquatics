package com.pwaquatics.shop.coupon;

import static com.pwaquatics.shop.support.AuthFixture.json;
import static com.pwaquatics.shop.support.AuthFixture.uniqueEmail;
import static com.pwaquatics.shop.support.TestRequests.uniqueClientIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pwaquatics.shop.customer.AddressApiTest;
import com.pwaquatics.shop.support.AuthFixture;
import com.pwaquatics.shop.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Coupon management, coupon figures and the coupon statistics (docs/API.md §3.1, §3.6). */
@IntegrationTest
@Sql(scripts = "/coupon/coupon-test-data.sql", executionPhase = ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = "/coupon/coupon-test-cleanup.sql", executionPhase = ExecutionPhase.AFTER_TEST_CLASS)
class AdminCouponApiTest {

    private static final String COUPONS = "/api/v1/admin/coupons";
    private static final String ALPHA = "88888888-0000-0000-0000-000000000201"; // 10.00, live
    private static final String METHOD = "TEST_COUPON_LIVE"; // 9.90
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Autowired MockMvc mvc;
    @Autowired AuthFixture auth;
    @Autowired JdbcTemplate jdbc;

    private Cookie admin;

    @BeforeEach
    void signInAdmin() throws Exception {
        admin = auth.adminSession(uniqueEmail());
    }

    @Test
    void onlyAdministratorsManageCouponsAndSeeTheirFigures() throws Exception {
        Cookie customer = auth.registeredSession(uniqueEmail());
        String someId = "88888888-0000-0000-0000-000000000401";
        for (String path : new String[] {
            COUPONS, COUPONS + "/" + someId, COUPONS + "/" + someId + "/usage", COUPONS + "/" + someId + "/redemptions",
            "/api/v1/admin/stats/coupons"
        }) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).cookie(customer)).andExpect(status().isForbidden());
        }
        mvc.perform(json(post(COUPONS), coupon("T8-ADM-NOPE", "PERCENTAGE", 1000, "")).cookie(customer))
                .andExpect(status().isForbidden());
        mvc.perform(delete(COUPONS + "/" + someId).cookie(customer).with(csrf())).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM coupons WHERE code = 'T8-ADM-NOPE'", Integer.class))
                .isZero();
    }

    @Test
    void theOwnerCreatesChangesAndSwitchesOffACoupon() throws Exception {
        LocalDate today = LocalDate.now(BERLIN);
        MvcResult created = mvc.perform(json(post(COUPONS), coupon("t8-adm-spring", "PERCENTAGE", 1250,
                                ",\"maxDiscountCents\":2000,\"minOrderCents\":1500,\"startsOn\":\"%s\",\"endsOn\":\"%s\""
                                        .formatted(today, today.plusDays(10))))
                        .cookie(admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("T8-ADM-SPRING"))
                .andExpect(jsonPath("$.percentage").value(1250))
                .andExpect(jsonPath("$.amount").value(nullValue()))
                .andExpect(jsonPath("$.maxDiscount.amount").value(2000))
                .andExpect(jsonPath("$.minOrder.amount").value(1500))
                .andExpect(jsonPath("$.startsOn").value(today.toString()))
                .andExpect(jsonPath("$.endsOn").value(today.plusDays(10).toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.timesRedeemed").value(0))
                .andReturn();
        String id = read(created, "$.id");
        int version = read(created, "$.version");

        // The code is unique regardless of case.
        mvc.perform(json(post(COUPONS), coupon("T8-ADM-SPRING", "FIXED_AMOUNT", 500, "")).cookie(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CODE_TAKEN"));
        mvc.perform(json(post(COUPONS), coupon("T8-ADM-CAP", "FIXED_AMOUNT", 500, ",\"maxDiscountCents\":100"))
                        .cookie(admin))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COUPON_VALUE_INVALID"));
        mvc.perform(json(post(COUPONS), coupon("T8-ADM-PCT", "PERCENTAGE", 10_001, "")).cookie(admin))
                .andExpect(jsonPath("$.code").value("COUPON_VALUE_INVALID"));
        mvc.perform(json(post(COUPONS), coupon("T8-ADM-DATES", "FREE_SHIPPING", 0,
                                ",\"startsOn\":\"%s\",\"endsOn\":\"%s\"".formatted(today, today.minusDays(1))))
                        .cookie(admin))
                .andExpect(jsonPath("$.code").value("RANGE_INVALID"));
        mvc.perform(json(post(COUPONS), coupon("x", "FREE_SHIPPING", 0, "")).cookie(admin))
                .andExpect(status().isBadRequest());

        // A customer can use it ...
        Cookie cart = guestCartWith(ALPHA, 2);
        mvc.perform(json(put("/api/v1/cart/coupon"), "{\"code\":\"t8-adm-spring\"}").cookie(cart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.discount.amount").value(250));

        // ... until the owner switches it off. An outdated version is refused.
        String off = """
                {"type": "PERCENTAGE", "value": 1250, "minOrderCents": 1500, "active": false, "version": %d}
                """;
        mvc.perform(json(put(COUPONS + "/" + id), off.formatted(version + 5)).cookie(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(json(put(COUPONS + "/" + id), off.formatted(version)).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("T8-ADM-SPRING"))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        mvc.perform(get("/api/v1/cart").cookie(cart))
                .andExpect(jsonPath("$.coupon.applied").value(false))
                .andExpect(jsonPath("$.coupon.problem").value("COUPON_INVALID"))
                .andExpect(jsonPath("$.totals.discount.amount").value(0));

        mvc.perform(get(COUPONS).param("status", "INACTIVE").cookie(admin))
                .andExpect(jsonPath("$[*].code").value(hasItem("T8-ADM-SPRING")));
        mvc.perform(get(COUPONS).param("status", "ACTIVE").cookie(admin))
                .andExpect(jsonPath("$[*].code").value(not(hasItem("T8-ADM-SPRING"))));
        mvc.perform(get(COUPONS).param("q", "adm-spr").cookie(admin))
                .andExpect(jsonPath("$[*].code").value(hasItem("T8-ADM-SPRING")));
        assertThat(jdbc.queryForList(
                        "SELECT action FROM audit_log WHERE entity_id = ?::uuid ORDER BY created_at", String.class, id))
                .containsExactly("COUPON_CREATED", "COUPON_UPDATED");
    }

    @Test
    void aUsedCouponShowsItsOrdersAndCanOnlyBeSwitchedOff() throws Exception {
        String id = read(mvc.perform(json(post(COUPONS), coupon("T8-ADM-USED", "FIXED_AMOUNT", 300, "")).cookie(admin))
                        .andExpect(status().isCreated())
                        .andReturn(),
                "$.id");

        Cookie customer = auth.registeredSession(uniqueEmail());
        String addressId = read(mvc.perform(json(post("/api/v1/account/addresses"), AddressApiTest.ADDRESS)
                                .cookie(customer))
                        .andExpect(status().isCreated())
                        .andReturn(),
                "$.id");
        mvc.perform(json(post("/api/v1/cart/items"), "{\"variantId\":\"%s\",\"quantity\":2}".formatted(ALPHA))
                        .cookie(customer))
                .andExpect(status().isOk());
        mvc.perform(json(put("/api/v1/cart/coupon"), "{\"code\":\"T8-ADM-USED\"}").cookie(customer))
                .andExpect(status().isOk());
        String orderNumber = read(mvc.perform(json(post("/api/v1/checkout/orders"), """
                                {"shippingAddressId": "%s", "shippingMethodCode": "%s", "paymentMethod": "BANK_TRANSFER",
                                 "acceptedTerms": true, "acceptedLiveAnimalConditions": true,
                                 "expectedTotal": {"amount": %d, "currency": "EUR"}}
                                """.formatted(addressId, METHOD, 2000 - 300 + 990))
                                .cookie(customer)
                                .header("Idempotency-Key", UUID.randomUUID().toString()))
                        .andExpect(status().isCreated())
                        .andReturn(),
                "$.orderNumber");

        mvc.perform(get(COUPONS + "/" + id).cookie(admin)).andExpect(jsonPath("$.timesRedeemed").value(1));
        mvc.perform(get(COUPONS + "/" + id + "/usage").cookie(admin))
                .andExpect(jsonPath("$.orders").value(1))
                .andExpect(jsonPath("$.saving.amount").value(300))
                .andExpect(jsonPath("$.revenue.amount").value(2690));
        mvc.perform(get(COUPONS + "/" + id + "/redemptions").cookie(admin))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.items[0].customerName").value("Anna Schmidt"))
                .andExpect(jsonPath("$.items[0].orderStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.items[0].saving.amount").value(300))
                .andExpect(jsonPath("$.items[0].released").value(false));

        LocalDate today = LocalDate.now(BERLIN);
        mvc.perform(get("/api/v1/admin/stats/coupons").param("from", today.toString()).param("to", today.toString())
                        .cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons[?(@.code == 'T8-ADM-USED')].orders").value(hasItem(1)))
                .andExpect(jsonPath("$.coupons[?(@.code == 'T8-ADM-USED')].saving.amount").value(hasItem(300)));

        mvc.perform(delete(COUPONS + "/" + id).cookie(admin).with(csrf()).with(uniqueClientIp()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUPON_IN_USE"));

        // An unused coupon can be deleted; carts that had it simply lose it.
        String unused = read(mvc.perform(json(post(COUPONS), coupon("T8-ADM-UNUSED", "FREE_SHIPPING", 0, ""))
                                .cookie(admin))
                        .andReturn(),
                "$.id");
        Cookie cart = guestCartWith(ALPHA, 1);
        mvc.perform(json(put("/api/v1/cart/coupon"), "{\"code\":\"T8-ADM-UNUSED\"}").cookie(cart))
                .andExpect(status().isOk());
        mvc.perform(delete(COUPONS + "/" + unused).cookie(admin).with(csrf()).with(uniqueClientIp()))
                .andExpect(status().isNoContent());
        mvc.perform(get(COUPONS + "/" + unused).cookie(admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/cart").cookie(cart)).andExpect(jsonPath("$.coupon").value(nullValue()));
    }

    // ------------------------------------------------------------------ helpers

    /** A new coupon; {@code extra} adds fields (starting with a comma). */
    private static String coupon(String code, String type, long value, String extra) {
        return """
                {"code": "%s", "description": "Test", "type": "%s", "value": %d, "active": true%s}
                """.formatted(code, type, value, extra);
    }

    private static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private Cookie guestCartWith(String variantId, int quantity) throws Exception {
        return mvc.perform(json(post("/api/v1/cart/items"),
                        "{\"variantId\":\"%s\",\"quantity\":%d}".formatted(variantId, quantity)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie("CART_TOKEN");
    }
}
