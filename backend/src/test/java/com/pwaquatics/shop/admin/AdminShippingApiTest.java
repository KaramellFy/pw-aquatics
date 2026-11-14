package com.pwaquatics.shop.admin;

import static com.pwaquatics.shop.admin.AdminTestSupport.read;
import static com.pwaquatics.shop.support.AuthFixture.json;
import static com.pwaquatics.shop.support.AuthFixture.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pwaquatics.shop.customer.AddressApiTest;
import com.pwaquatics.shop.support.AuthFixture;
import com.pwaquatics.shop.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Shipping methods, the shipping pause and the public shop info (docs/API.md §2.3, §3.7). */
@IntegrationTest
@Sql(scripts = "/order/order-test-data.sql", executionPhase = ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = "/order/order-test-cleanup.sql", executionPhase = ExecutionPhase.AFTER_TEST_CLASS)
class AdminShippingApiTest {

    private static final String METHODS = "/api/v1/admin/shipping-methods";
    private static final String PAUSE = "/api/v1/admin/settings/shipping-pause";
    private static final String ALPHA_SINGLE = "22222222-0000-0000-0000-000000000201"; // live animal
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired MockMvc mvc;
    @Autowired AuthFixture auth;
    @Autowired JdbcTemplate jdbc;

    private Cookie admin;

    @BeforeEach
    void signInAdmin() throws Exception {
        admin = auth.adminSession(uniqueEmail());
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM shop_settings WHERE key = 'shipping.pause'");
        jdbc.update("DELETE FROM shipping_methods WHERE code LIKE 'ADMIN_TEST_%'");
    }

    @Test
    void onlyAdministratorsChangeShippingSettings() throws Exception {
        Cookie customer = auth.registeredSession(uniqueEmail());
        for (String path : new String[] {METHODS, PAUSE}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).cookie(customer)).andExpect(status().isForbidden());
        }
        mvc.perform(json(post(METHODS), method("ADMIN_TEST_X", "{}")).cookie(customer)).andExpect(status().isForbidden());
        mvc.perform(json(put(PAUSE), "{\"active\":true}").cookie(customer)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shop_settings WHERE key = 'shipping.pause'", Integer.class))
                .isZero();
    }

    @Test
    void aNewMethodIsOfferedAtCheckoutAndCanBeChangedAndSwitchedOff() throws Exception {
        String id = read(mvc.perform(json(post(METHODS), method("ADMIN_TEST_AT", "{}")).cookie(admin))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.code").value("ADMIN_TEST_AT"))
                        .andExpect(jsonPath("$.price.amount").value(1490))
                        .andExpect(jsonPath("$.freeFrom.amount").value(15000))
                        .andExpect(jsonPath("$.dispatchDays").value(contains("MONDAY", "TUESDAY")))
                        .andExpect(jsonPath("$.countries").value(contains("AT", "DE")))
                        .andExpect(jsonPath("$.version").value(0))
                        .andReturn(), "$.id");
        mvc.perform(get(METHODS).cookie(admin)).andExpect(jsonPath("$[*].code").value(hasItem("ADMIN_TEST_AT")));

        Customer customer = customerWithAnimal();
        mvc.perform(get("/api/v1/checkout/shipping-methods").param("country", "AT").cookie(customer.session()))
                .andExpect(jsonPath("$.methods[*].code").value(contains("ADMIN_TEST_AT")))
                .andExpect(jsonPath("$.methods[0].price.amount").value(1490));

        // Price up, Austria dropped; the code stays as it is.
        mvc.perform(json(put(METHODS + "/" + id), method("SOMETHING_ELSE", """
                        {"priceCents": 1590, "countries": ["de"], "version": 0}
                        """)).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ADMIN_TEST_AT"))
                .andExpect(jsonPath("$.price.amount").value(1590))
                .andExpect(jsonPath("$.countries").value(contains("DE")))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/v1/checkout/shipping-methods").param("country", "AT").cookie(customer.session()))
                .andExpect(jsonPath("$.methods[*].code").value(not(hasItem("ADMIN_TEST_AT"))));
        // A stale editor is told to reload.
        mvc.perform(json(put(METHODS + "/" + id), method("ADMIN_TEST_AT", "{\"version\": 0}")).cookie(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        mvc.perform(json(put(METHODS + "/" + id), method("ADMIN_TEST_AT", "{\"active\": false, \"version\": 1}"))
                        .cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/v1/checkout/shipping-methods").param("country", "DE").cookie(customer.session()))
                .andExpect(jsonPath("$.methods[*].code").value(not(hasItem("ADMIN_TEST_AT"))));

        assertThat(jdbc.queryForList("SELECT action FROM audit_log WHERE entity_id = ?::uuid ORDER BY id",
                        String.class, id))
                .containsExactly("SHIPPING_METHOD_CREATED", "SHIPPING_METHOD_UPDATED", "SHIPPING_METHOD_UPDATED");
    }

    @Test
    void incompleteOrInconsistentMethodsAreRefused() throws Exception {
        mvc.perform(json(post(METHODS), method("ADMIN_TEST_DUP", "{}")).cookie(admin)).andExpect(status().isCreated());
        mvc.perform(json(post(METHODS), method("ADMIN_TEST_DUP", "{}")).cookie(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CODE_TAKEN"));
        mvc.perform(json(post(METHODS), method("ADMIN_TEST_DUP", "{\"code\": null}")).cookie(admin))
                .andExpect(status().isBadRequest());

        for (String broken : new String[] {
                "{\"code\": \"lower-case\"}",
                "{\"dispatchDays\": []}",
                "{\"dispatchDays\": [\"FUNDAY\"]}",
                "{\"countries\": []}",
                "{\"countries\": [\"Germany\"]}",
                "{\"priceCents\": -1}",
                "{\"freeShippingThresholdCents\": 0}",
                "{\"name\": \" \"}"}) {
            mvc.perform(json(post(METHODS), method("ADMIN_TEST_BAD", broken)).cookie(admin))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(json(post(METHODS), method("ADMIN_TEST_BAD", "{\"countries\": [\"XX\"]}")).cookie(admin))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COUNTRY_INVALID"));
        mvc.perform(json(post(METHODS), method("ADMIN_TEST_BAD", "{\"estimatedDaysMin\": 3, \"estimatedDaysMax\": 1}"))
                        .cookie(admin))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DELIVERY_ESTIMATE_INVALID"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shipping_methods WHERE code = 'ADMIN_TEST_BAD'",
                        Integer.class))
                .isZero();
    }

    @Test
    void theShippingPauseShowsABannerAndMovesDispatchDates() throws Exception {
        mvc.perform(get("/api/v1/shop-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingPause").value(nullValue()));
        mvc.perform(get(PAUSE).cookie(admin))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.inForce").value(false));

        LocalDate until = LocalDate.now(BERLIN).plusDays(10);
        mvc.perform(json(put(PAUSE), """
                        {"active":true,"message":"  Heat wave – live animals ship again when it cools down. ","until":"%s"}
                        """.formatted(until)).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.inForce").value(true))
                .andExpect(jsonPath("$.message").value("Heat wave – live animals ship again when it cools down."))
                .andExpect(jsonPath("$.until").value(until.toString()));
        mvc.perform(get("/api/v1/shop-info"))
                .andExpect(jsonPath("$.shippingPause.message")
                        .value("Heat wave – live animals ship again when it cools down."))
                .andExpect(jsonPath("$.shippingPause.until").value(until.toString()));

        // Live animals leave after the pause.
        Customer customer = customerWithAnimal();
        String dispatch = read(mvc.perform(get("/api/v1/checkout/shipping-methods").param("country", "DE")
                        .cookie(customer.session()))
                .andExpect(jsonPath("$.paused").value(true))
                .andReturn(), "$.methods[0].plannedDispatchDate");
        assertThat(LocalDate.parse(dispatch)).isAfter(until);

        // A pause that has already ended cannot be set; switching off clears it.
        mvc.perform(json(put(PAUSE), "{\"active\":true,\"until\":\"%s\"}".formatted(LocalDate.now(BERLIN).minusDays(1)))
                        .cookie(admin))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DATE_INVALID"));
        mvc.perform(json(put(PAUSE), "{\"active\":true,\"message\":\"%s\"}".formatted("x".repeat(301))).cookie(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(json(put(PAUSE), "{\"active\":false,\"message\":\"old\"}").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.message").value(nullValue()));
        mvc.perform(get("/api/v1/shop-info")).andExpect(jsonPath("$.shippingPause").value(nullValue()));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_log WHERE action = 'SHIPPING_PAUSE_CHANGED' AND created_at > now() - interval '1 minute'",
                        Integer.class))
                .isGreaterThanOrEqualTo(2);
    }

    // ------------------------------------------------------------------ helpers

    private record Customer(Cookie session) {}

    private Customer customerWithAnimal() throws Exception {
        Cookie session = auth.registeredSession(uniqueEmail());
        mvc.perform(json(post("/api/v1/account/addresses"), AddressApiTest.ADDRESS).cookie(session))
                .andExpect(status().isCreated());
        mvc.perform(json(post("/api/v1/cart/items"), "{\"variantId\":\"%s\",\"quantity\":1}".formatted(ALPHA_SINGLE))
                        .cookie(session))
                .andExpect(status().isOk());
        return new Customer(session);
    }

    /** A complete method as JSON; the fields in {@code overrides} (a JSON object) replace the defaults. */
    private static String method(String code, String overrides) {
        ObjectNode body = (ObjectNode) JSON.readTree("""
                {"code": "%s", "name": "Admin test express", "description": "Overnight, insulated box",
                 "carrier": "DHL", "priceCents": 1490, "freeShippingThresholdCents": 15000,
                 "liveAnimalsAllowed": true, "dispatchDays": ["TUESDAY", "MONDAY"],
                 "estimatedDaysMin": 1, "estimatedDaysMax": 2, "countries": ["DE", "at"],
                 "active": true, "sortOrder": 50, "version": 0}
                """.formatted(code));
        body.setAll((ObjectNode) JSON.readTree(overrides));
        return body.toString();
    }
}
