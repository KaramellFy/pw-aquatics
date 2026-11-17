package com.pwaquatics.shop.identity;

import static com.pwaquatics.shop.support.TestRequests.uniqueClientIp;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pwaquatics.shop.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Without Google credentials (the usual state until the owner sets them up) nothing is offered. */
@IntegrationTest
@TestPropertySource(properties = {"app.google.client-id=", "app.google.client-secret="})
class GoogleSwitchedOffTest {

    @Autowired MockMvc mvc;

    @Test
    void googleIsNotOffered() throws Exception {
        mvc.perform(get("/api/v1/auth/sign-in-options")).andExpect(jsonPath("$.google").value(false));
        mvc.perform(get("/api/v1/auth/google").param("returnTo", "/account").with(uniqueClientIp()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/auth/google/callback?code=x&state=y").with(uniqueClientIp()))
                .andExpect(status().isFound());
    }
}
