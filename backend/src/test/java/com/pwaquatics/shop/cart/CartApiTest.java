package com.pwaquatics.shop.cart;

import static com.pwaquatics.shop.support.AuthFixture.PASSWORD;
import static com.pwaquatics.shop.support.AuthFixture.json;
import static com.pwaquatics.shop.support.AuthFixture.uniqueEmail;
import static com.pwaquatics.shop.support.TestRequests.uniqueClientIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pwaquatics.shop.common.crypto.Tokens;
import com.pwaquatics.shop.support.AuthFixture;
import com.pwaquatics.shop.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
@Sql(scripts = "/cart/cart-test-data.sql", executionPhase = ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = "/cart/cart-test-cleanup.sql", executionPhase = ExecutionPhase.AFTER_TEST_CLASS)
class CartApiTest {

    private static final String ALPHA_SINGLE = "11111111-0000-0000-0000-000000000201"; // 3.00, stock 12
    private static final String ALPHA_TEN = "11111111-0000-0000-0000-000000000202"; // 25.00, pack of 10
    private static final String BETA = "11111111-0000-0000-0000-000000000203"; // 2.00, stock 3
    private static final String SOLD_OUT = "11111111-0000-0000-0000-000000000204";
    private static final String DRAFT = "11111111-0000-0000-0000-000000000205";
    private static final String BETA_PRODUCT = "11111111-0000-0000-0000-000000000102";
    private static final String ALPHA_PRODUCT = "11111111-0000-0000-0000-000000000101";

    @Autowired MockMvc mvc;
    @Autowired AuthFixture auth;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aGuestCartIsCreatedOnTheFirstAddAndFoundAgainByItsCookie() throws Exception {
        mvc.perform(get("/api/v1/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totals.total.amount").value(0))
                .andExpect(cookie().doesNotExist("CART_TOKEN"));

        MvcResult added = add(null, ALPHA_SINGLE, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productName").value("Cart Alpha"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice.amount").value(300))
                .andExpect(jsonPath("$.items[0].lineTotal.amount").value(600))
                .andExpect(jsonPath("$.items[0].maxQuantity").value(12))
                .andExpect(jsonPath("$.totals.subtotal.amount").value(600))
                .andExpect(jsonPath("$.totals.includedTax.amount").value(96))
                .andExpect(jsonPath("$.itemCount").value(2))
                .andReturn();

        Cookie cart = added.getResponse().getCookie("CART_TOKEN");
        assertThat(cart).isNotNull();
        assertThat(cart.isHttpOnly()).isTrue();
        assertThat(cart.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(cart.getMaxAge()).isEqualTo(30 * 24 * 3600);
        // Only a hash of the token is stored.
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM carts WHERE anonymous_token_hash = ?", Integer.class,
                        Tokens.sha256Hex(cart.getValue())))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM carts WHERE anonymous_token_hash = ?", Integer.class, cart.getValue()))
                .isZero();

        mvc.perform(get("/api/v1/cart").cookie(cart))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.totals.total.amount").value(600));
    }

    @Test
    void addingAgainIncreasesTheQuantityButNeverBeyondTheStock() throws Exception {
        Cookie cart = guestCartWith(BETA, 2);

        add(cart, BETA, 1).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(3));
        add(cart, BETA, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        // Packs draw from the same stock: 12 animals are enough for one pack of ten, not two.
        add(cart, ALPHA_TEN, 2).andExpect(status().isConflict());
        add(cart, ALPHA_TEN, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].variantName").value(contains("1 animal", "10 animals")))
                .andExpect(jsonPath("$.totals.subtotal.amount").value(3 * 200 + 2500));
    }

    @Test
    void packSizesOfOneProductShareItsStock() throws Exception {
        // Alpha has 12 animals: a pack of ten leaves room for two singles, not three.
        Cookie cart = guestCartWith(ALPHA_TEN, 1);
        add(cart, ALPHA_SINGLE, 3)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        add(cart, ALPHA_SINGLE, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].maxQuantity").value(contains(1, 2)));
        mvc.perform(json(patch("/api/v1/cart/items/" + itemId(cart, 1)), "{\"quantity\":3}").cookie(cart))
                .andExpect(status().isConflict());

        // With one single less in the cart, the stepper offers the freed animal again.
        mvc.perform(json(patch("/api/v1/cart/items/" + itemId(cart, 1)), "{\"quantity\":1}").cookie(cart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].maxQuantity").value(contains(1, 2)));

        // An animal dies: the lines no longer fit together, the later one gives way.
        try {
            jdbc.update("UPDATE products SET stock_units = 10 WHERE id = ?::uuid", ALPHA_PRODUCT);
            mvc.perform(get("/api/v1/cart").cookie(cart))
                    .andExpect(jsonPath("$.items[*].available").value(contains(true, false)))
                    .andExpect(jsonPath("$.totals.total.amount").value(2500))
                    .andExpect(jsonPath("$.warnings[0].code").value("ITEM_UNAVAILABLE"));
        } finally {
            jdbc.update("UPDATE products SET stock_units = 12 WHERE id = ?::uuid", ALPHA_PRODUCT);
        }
    }

    @Test
    void productsThatCannotBeBoughtAreRefusedWithoutCreatingACart() throws Exception {
        for (String variant : new String[] {SOLD_OUT, DRAFT}) {
            add(null, variant, 1)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PRODUCT_UNAVAILABLE"))
                    .andExpect(cookie().doesNotExist("CART_TOKEN"));
        }
        add(null, "99999999-0000-0000-0000-000000000000", 1).andExpect(status().isNotFound());
    }

    @Test
    void pricesSentByTheClientAreIgnored() throws Exception {
        mvc.perform(json(post("/api/v1/cart/items"), """
                        {"variantId":"%s","quantity":1,"unitPrice":{"amount":1,"currency":"EUR"},"price":1,"lineTotal":1}
                        """.formatted(ALPHA_SINGLE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unitPrice.amount").value(300))
                .andExpect(jsonPath("$.totals.total.amount").value(300));
    }

    @Test
    void quantitiesCanBeChangedAndLinesRemoved() throws Exception {
        Cookie cart = guestCartWith(ALPHA_SINGLE, 1);
        String itemId = itemId(cart, 0);

        mvc.perform(json(patch("/api/v1/cart/items/" + itemId), "{\"quantity\":5}").cookie(cart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(5))
                .andExpect(jsonPath("$.totals.total.amount").value(1500));
        mvc.perform(json(patch("/api/v1/cart/items/" + itemId), "{\"quantity\":13}").cookie(cart))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        for (String invalid : new String[] {"{\"quantity\":0}", "{\"quantity\":21}", "{}"}) {
            mvc.perform(json(patch("/api/v1/cart/items/" + itemId), invalid).cookie(cart))
                    .andExpect(status().isBadRequest());
        }

        add(cart, BETA, 1).andExpect(jsonPath("$.items", hasSize(2)));
        mvc.perform(delete("/api/v1/cart/items/" + itemId).cookie(cart).with(csrf()).with(uniqueClientIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].productName").value(contains("Cart Beta")));
        mvc.perform(delete("/api/v1/cart").cookie(cart).with(csrf()).with(uniqueClientIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.itemCount").value(0));
    }

    @Test
    void anotherVisitorsCartLinesAreNotFound() throws Exception {
        Cookie owner = guestCartWith(ALPHA_SINGLE, 1);
        Cookie stranger = guestCartWith(BETA, 1);
        String itemId = itemId(owner, 0);

        mvc.perform(json(patch("/api/v1/cart/items/" + itemId), "{\"quantity\":2}").cookie(stranger))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/cart/items/" + itemId).cookie(stranger).with(csrf()).with(uniqueClientIp()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/cart/items/" + itemId).with(csrf()).with(uniqueClientIp()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/cart").cookie(owner)).andExpect(jsonPath("$.items[0].quantity").value(1));
    }

    @Test
    void theCartFollowsStockChangesAndReportsThem() throws Exception {
        Cookie cart = guestCartWith(BETA, 3);
        try {
            jdbc.update("UPDATE products SET stock_units = 1 WHERE id = ?::uuid", BETA_PRODUCT);
            mvc.perform(get("/api/v1/cart").cookie(cart))
                    .andExpect(jsonPath("$.items[0].quantity").value(1))
                    .andExpect(jsonPath("$.totals.total.amount").value(200))
                    .andExpect(jsonPath("$.warnings[0].code").value("QUANTITY_REDUCED"))
                    .andExpect(jsonPath("$.warnings[0].productName").value("Cart Beta"))
                    .andExpect(jsonPath("$.warnings[0].quantity").value(1));
            // The reduction was saved, so it is reported only once.
            mvc.perform(get("/api/v1/cart").cookie(cart)).andExpect(jsonPath("$.warnings", hasSize(0)));

            jdbc.update("UPDATE products SET stock_units = 0 WHERE id = ?::uuid", BETA_PRODUCT);
            mvc.perform(get("/api/v1/cart").cookie(cart))
                    .andExpect(jsonPath("$.items[0].available").value(false))
                    .andExpect(jsonPath("$.items[0].maxQuantity").value(0))
                    .andExpect(jsonPath("$.totals.total.amount").value(0))
                    .andExpect(jsonPath("$.itemCount").value(0))
                    .andExpect(jsonPath("$.warnings[0].code").value("ITEM_UNAVAILABLE"));
        } finally {
            jdbc.update("UPDATE products SET stock_units = 3 WHERE id = ?::uuid", BETA_PRODUCT);
        }
    }

    @Test
    void theGuestCartIsMergedIntoTheCustomersCartAfterSigningIn() throws Exception {
        String email = uniqueEmail();
        Cookie session = auth.registeredSession(email);
        add(session, BETA, 2).andExpect(status().isOk()).andExpect(cookie().doesNotExist("CART_TOKEN"));
        mvc.perform(post("/api/v1/auth/logout").cookie(session).with(csrf())).andExpect(status().isNoContent());

        // Browsing signed out: a separate guest cart.
        Cookie guest = guestCartWith(BETA, 2);
        add(guest, ALPHA_SINGLE, 1).andExpect(jsonPath("$.items", hasSize(2)));

        // Signing in: the next cart request carries both cookies and merges the carts.
        Cookie newSession = auth.login(email, PASSWORD);
        mvc.perform(get("/api/v1/cart").cookie(newSession, guest))
                .andExpect(status().isOk())
                // 2 + 2 Beta would exceed the stock of 3.
                .andExpect(jsonPath("$.items[?(@.productName == 'Cart Beta')].quantity").value(3))
                .andExpect(jsonPath("$.items[?(@.productName == 'Cart Alpha')].quantity").value(1))
                .andExpect(cookie().maxAge("CART_TOKEN", 0));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM carts WHERE anonymous_token_hash = ?", Integer.class,
                        Tokens.sha256Hex(guest.getValue())))
                .isZero();

        // The cart belongs to the account now: it survives signing out and in again.
        mvc.perform(post("/api/v1/auth/logout").cookie(newSession).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/cart")).andExpect(jsonPath("$.items", hasSize(0)));
        Cookie later = auth.login(email, PASSWORD);
        mvc.perform(get("/api/v1/cart").cookie(later))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totals.total.amount").value(3 * 200 + 300));
    }

    @Test
    void deletingTheAccountRemovesItsCart() throws Exception {
        String email = uniqueEmail();
        Cookie session = auth.registeredSession(email);
        add(session, ALPHA_SINGLE, 1).andExpect(status().isOk());
        String userId = jdbc.queryForObject("SELECT id::text FROM users WHERE email = ?::citext", String.class, email);

        mvc.perform(json(delete("/api/v1/account"), "{\"currentPassword\":\"%s\"}".formatted(PASSWORD)).cookie(session))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM carts WHERE user_id = ?::uuid", Integer.class, userId))
                .isZero();
    }

    @Test
    void cartChangesNeedACsrfTokenAndValidInput() throws Exception {
        mvc.perform(post("/api/v1/cart/items").contentType("application/json")
                        .content("{\"variantId\":\"%s\",\"quantity\":1}".formatted(ALPHA_SINGLE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(json(post("/api/v1/cart/items"), "{\"variantId\":\"not-a-uuid\",\"quantity\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/v1/cart/items"), "{\"variantId\":\"%s\",\"quantity\":0}".formatted(ALPHA_SINGLE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        // A malformed cookie is treated like no cookie.
        mvc.perform(get("/api/v1/cart").cookie(new Cookie("CART_TOKEN", "x")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    private ResultActions add(Cookie cookie, String variantId, int quantity) throws Exception {
        var request = json(post("/api/v1/cart/items"),
                "{\"variantId\":\"%s\",\"quantity\":%d}".formatted(variantId, quantity));
        return mvc.perform(cookie == null ? request : request.cookie(cookie));
    }

    private Cookie guestCartWith(String variantId, int quantity) throws Exception {
        return add(null, variantId, quantity).andExpect(status().isOk()).andReturn().getResponse()
                .getCookie("CART_TOKEN");
    }

    private String itemId(Cookie cart, int index) throws Exception {
        String body = mvc.perform(get("/api/v1/cart").cookie(cart)).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.items[" + index + "].id");
    }
}
