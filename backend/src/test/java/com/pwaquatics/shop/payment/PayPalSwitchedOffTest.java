package com.pwaquatics.shop.payment;

import static com.pwaquatics.shop.support.AuthFixture.json;
import static com.pwaquatics.shop.support.AuthFixture.uniqueEmail;
import static com.pwaquatics.shop.support.TestRequests.uniqueClientIp;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pwaquatics.shop.customer.AddressApiTest;
import com.pwaquatics.shop.support.AuthFixture;
import com.pwaquatics.shop.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A shop without PayPal credentials (the usual state until the owner sets them up): it starts,
 * PayPal is not offered, an order cannot be placed for it, and the webhook does not exist.
 */
@IntegrationTest
@TestPropertySource(properties = {"app.paypal.client-id=", "app.paypal.client-secret=", "app.paypal.webhook-id="})
@Sql(scripts = "/order/order-test-data.sql", executionPhase = ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = "/order/order-test-cleanup.sql", executionPhase = ExecutionPhase.AFTER_TEST_CLASS)
class PayPalSwitchedOffTest {

    private static final String ALPHA_SINGLE = "22222222-0000-0000-0000-000000000201";
    private static final String LIVE = "TEST_LIVE";

    @Autowired MockMvc mvc;
    @Autowired AuthFixture auth;

    @Test
    void payPalIsNotOfferedAndCannotBeChosen() throws Exception {
        Cookie session = auth.registeredSession(uniqueEmail());
        String addressId = JsonPath.read(mvc.perform(json(post("/api/v1/account/addresses"), AddressApiTest.ADDRESS)
                        .cookie(session))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(json(post("/api/v1/cart/items"), "{\"variantId\":\"%s\",\"quantity\":1}".formatted(ALPHA_SINGLE))
                        .cookie(session))
                .andExpect(status().isOk());

        mvc.perform(json(post("/api/v1/checkout/quote"), """
                        {"shippingAddressId": "%s", "shippingMethodCode": "%s"}
                        """.formatted(addressId, LIVE)).cookie(session))
                .andExpect(jsonPath("$.paymentMethods").value(contains("STRIPE", "BANK_TRANSFER")));

        mvc.perform(json(post("/api/v1/checkout/orders"), """
                        {"shippingAddressId": "%s", "shippingMethodCode": "%s", "paymentMethod": "PAYPAL",
                         "acceptedTerms": true, "acceptedLiveAnimalConditions": true,
                         "expectedTotal": {"amount": 1290, "currency": "EUR"}}
                        """.formatted(addressId, LIVE))
                        .cookie(session)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_METHOD_UNAVAILABLE"));
    }

    @Test
    void theWebhookDoesNotExist() throws Exception {
        mvc.perform(post("/api/v1/webhooks/paypal").contentType("application/json").content("{}")
                        .with(uniqueClientIp()))
                .andExpect(status().isNotFound());
    }
}
