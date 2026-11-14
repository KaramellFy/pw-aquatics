package com.pwaquatics.shop.admin;

import static com.pwaquatics.shop.support.AuthFixture.PASSWORD;
import static com.pwaquatics.shop.support.AuthFixture.json;
import static com.pwaquatics.shop.support.AuthFixture.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pwaquatics.shop.customer.AddressApiTest;
import com.pwaquatics.shop.support.AuthFixture;
import com.pwaquatics.shop.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** Customer accounts in the admin area (docs/API.md §3.7). */
@IntegrationTest
@Sql(scripts = "/order/order-test-data.sql", executionPhase = ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = "/order/order-test-cleanup.sql", executionPhase = ExecutionPhase.AFTER_TEST_CLASS)
class AdminCustomerApiTest {

    private static final String ALPHA_SINGLE = "22222222-0000-0000-0000-000000000201"; // 3.00, live
    private static final long ONE_ANIMAL_TOTAL = 1290; // 3.00 + 9.90 shipping
    private static final String CUSTOMERS = "/api/v1/admin/customers";

    @Autowired MockMvc mvc;
    @Autowired AuthFixture auth;
    @Autowired JdbcTemplate jdbc;

    private String adminEmail;
    private Cookie admin;

    @BeforeEach
    void signInAdmin() throws Exception {
        adminEmail = uniqueEmail();
        admin = auth.adminSession(adminEmail);
    }

    @Test
    void onlyAdministratorsSeeCustomers() throws Exception {
        Cookie customer = auth.registeredSession(uniqueEmail());
        String id = userId(adminEmail);

        mvc.perform(get(CUSTOMERS)).andExpect(status().isUnauthorized());
        mvc.perform(get(CUSTOMERS).cookie(customer)).andExpect(status().isForbidden());
        mvc.perform(get(CUSTOMERS + "/" + id).cookie(customer)).andExpect(status().isForbidden());
        mvc.perform(json(post(CUSTOMERS + "/" + id + "/lock"), "{}").cookie(customer))
                .andExpect(status().isForbidden());
    }

    @Test
    void theListAndDetailShowOrdersAndWhatWasSpent() throws Exception {
        String email = uniqueEmail();
        Cookie session = auth.registeredSession(email);
        String addressId = read(mvc.perform(json(post("/api/v1/account/addresses"), AddressApiTest.ADDRESS)
                        .cookie(session)).andExpect(status().isCreated()).andReturn(), "$.id");
        String paid = placeOrder(session, addressId);
        String unpaid = placeOrder(session, addressId);
        mvc.perform(json(post("/api/v1/admin/orders/" + paid + "/mark-paid"), "{}").cookie(admin))
                .andExpect(status().isOk());
        String id = userId(email);

        mvc.perform(get(CUSTOMERS).param("q", email).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].name").value("Anna Schmidt"))
                .andExpect(jsonPath("$.items[0].email").value(email))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].emailVerified").value(true))
                .andExpect(jsonPath("$.items[0].orderCount").value(2))
                // Only the paid order counts.
                .andExpect(jsonPath("$.items[0].lifetimeValue.amount").value(ONE_ANIMAL_TOTAL))
                .andExpect(jsonPath("$.items[0].lastOrderAt").isNotEmpty());

        mvc.perform(get(CUSTOMERS + "/" + id).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.firstName").value("Anna"))
                .andExpect(jsonPath("$.lockable").value(true))
                .andExpect(jsonPath("$.orderCount").value(2))
                .andExpect(jsonPath("$.paidOrderCount").value(1))
                .andExpect(jsonPath("$.lifetimeValue.amount").value(ONE_ANIMAL_TOTAL))
                .andExpect(jsonPath("$.averageOrderValue.amount").value(ONE_ANIMAL_TOTAL))
                .andExpect(jsonPath("$.addresses", hasSize(1)))
                .andExpect(jsonPath("$.addresses[0].city").value("Berlin"))
                .andExpect(jsonPath("$.addresses[0].defaultShipping").value(true))
                .andExpect(jsonPath("$.orders[*].orderNumber").value(contains(unpaid, paid)))
                .andExpect(jsonPath("$.orders[1].paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.orders[1].itemCount").value(1));

        // A customer without orders.
        String newcomer = uniqueEmail();
        auth.registeredSession(newcomer);
        mvc.perform(get(CUSTOMERS + "/" + userId(newcomer)).cookie(admin))
                .andExpect(jsonPath("$.orderCount").value(0))
                .andExpect(jsonPath("$.lifetimeValue.amount").value(0))
                .andExpect(jsonPath("$.averageOrderValue").value(nullValue()))
                .andExpect(jsonPath("$.orders", hasSize(0)));

        // Administrators, unknown and deleted accounts are not customers here.
        mvc.perform(get(CUSTOMERS).param("q", adminEmail).cookie(admin)).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get(CUSTOMERS + "/" + userId(adminEmail)).cookie(admin)).andExpect(status().isNotFound());
        mvc.perform(get(CUSTOMERS + "/" + UUID.randomUUID()).cookie(admin)).andExpect(status().isNotFound());
        mvc.perform(get(CUSTOMERS).param("status", "ANONYMISED").cookie(admin)).andExpect(status().isBadRequest());
        mvc.perform(get(CUSTOMERS).param("size", "101").cookie(admin)).andExpect(status().isBadRequest());
    }

    @Test
    void aLockedAccountIsSignedOutAndCannotSignInUntilUnlocked() throws Exception {
        String email = uniqueEmail();
        Cookie session = auth.registeredSession(email);
        String id = userId(email);
        mvc.perform(get("/api/v1/account/profile").cookie(session)).andExpect(status().isOk());

        action(id, "/lock", "{\"reason\":\"Chargebacks\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));
        // The open session has ended, and signing in is refused.
        mvc.perform(get("/api/v1/account/profile").cookie(session)).andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/v1/auth/login"), "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        mvc.perform(get(CUSTOMERS).param("status", "LOCKED").param("q", email).cookie(admin))
                .andExpect(jsonPath("$.totalItems").value(1));
        // Locking twice changes nothing.
        action(id, "/lock", "{}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("LOCKED"));

        action(id, "/unlock", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        auth.login(email, PASSWORD);

        assertThat(jdbc.queryForList(
                        "SELECT action FROM audit_log WHERE entity_id = ?::uuid AND action LIKE 'ACCOUNT_%_BY_SHOP' ORDER BY id",
                        String.class, id))
                .containsExactly("ACCOUNT_LOCKED_BY_SHOP", "ACCOUNT_UNLOCKED_BY_SHOP");
        assertThat(jdbc.queryForObject(
                        "SELECT details->>'reason' FROM audit_log WHERE entity_id = ?::uuid AND action = 'ACCOUNT_LOCKED_BY_SHOP'",
                        String.class, id))
                .isEqualTo("Chargebacks");
    }

    @Test
    void administratorsCannotBeLockedHere() throws Exception {
        String otherAdmin = uniqueEmail();
        auth.adminSession(otherAdmin);

        for (String email : new String[] {adminEmail, otherAdmin}) {
            action(userId(email), "/lock", "{}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_LOCKABLE"));
        }
        action(UUID.randomUUID().toString(), "/lock", "{}").andExpect(status().isNotFound());
        action(userId(adminEmail), "/lock", "{\"reason\":\"%s\"}".formatted("x".repeat(501)))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE email = ?::citext", String.class, otherAdmin))
                .isEqualTo("ACTIVE");
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions action(String id, String path, String body) throws Exception {
        return mvc.perform(json(post(CUSTOMERS + "/" + id + path), body).cookie(admin));
    }

    private String placeOrder(Cookie session, String addressId) throws Exception {
        mvc.perform(json(post("/api/v1/cart/items"), "{\"variantId\":\"%s\",\"quantity\":1}".formatted(ALPHA_SINGLE))
                        .cookie(session))
                .andExpect(status().isOk());
        return read(mvc.perform(json(post("/api/v1/checkout/orders"), """
                                {"shippingAddressId": "%s", "shippingMethodCode": "TEST_LIVE",
                                 "paymentMethod": "BANK_TRANSFER", "acceptedTerms": true,
                                 "acceptedLiveAnimalConditions": true,
                                 "expectedTotal": {"amount": %d, "currency": "EUR"}}
                                """.formatted(addressId, ONE_ANIMAL_TOTAL))
                        .cookie(session)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isCreated())
                .andReturn(), "$.orderNumber");
    }

    private String userId(String email) {
        return jdbc.queryForObject("SELECT id::text FROM users WHERE email = ?::citext", String.class, email);
    }

    private static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
