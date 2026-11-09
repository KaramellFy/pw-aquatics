package com.pwaquatics.shop.content;

import static com.pwaquatics.shop.support.TestRequests.uniqueClientIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pwaquatics.shop.content.infrastructure.ContactMessageRepository;
import com.pwaquatics.shop.notification.domain.OutboxMessage;
import com.pwaquatics.shop.notification.infrastructure.OutboxMessageRepository;
import com.pwaquatics.shop.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@IntegrationTest
class ContactControllerTest {

    private static final String VALID = """
            {"name":"Anna Schmidt","email":"anna@example.com","subject":"Question about care",
             "message":"How many shrimp can I keep in a 30 litre tank?","orderNumber":"pw-2027-000123","website":""}
            """;

    @Autowired MockMvc mvc;
    @Autowired ContactMessageRepository contactMessages;
    @Autowired OutboxMessageRepository outbox;

    @BeforeEach
    void clean() {
        contactMessages.deleteAll();
        outbox.deleteAll();
    }

    @Test
    void storesTheMessageAndQueuesAnEmailToTheShopInbox() throws Exception {
        mvc.perform(contact(VALID).with(csrf())).andExpect(status().isAccepted());

        assertThat(contactMessages.findAll()).singleElement().satisfies(message -> {
            assertThat(message.getEmail()).isEqualTo("anna@example.com");
            assertThat(message.getOrderNumber()).isEqualTo("PW-2027-000123");
        });
        assertThat(outbox.findAll()).singleElement().satisfies(message -> {
            assertThat(message.getType()).isEqualTo(OutboxMessage.TYPE_EMAIL);
            assertThat(message.getPayload().to()).isEqualTo("shop@test.local");
            assertThat(message.getPayload().replyTo()).isEqualTo("anna@example.com");
            assertThat(message.getPayload().body()).contains("30 litre tank");
        });
    }

    @Test
    void rejectsInvalidInputWithFieldErrors() throws Exception {
        mvc.perform(contact("""
                        {"name":"","email":"not-an-email","subject":"Hi","message":"too short"}
                        """).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].code").value("NOT_BLANK"))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')].code").value("EMAIL"))
                .andExpect(jsonPath("$.errors[?(@.field == 'message')].code").value("SIZE"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
        assertThat(contactMessages.count()).isZero();
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mvc.perform(contact("{not json").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void requiresACsrfToken() throws Exception {
        mvc.perform(contact(VALID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        assertThat(contactMessages.count()).isZero();
    }

    @Test
    void silentlyDropsHoneypotSubmissions() throws Exception {
        mvc.perform(contact(VALID.replace("\"website\":\"\"", "\"website\":\"https://spam.example\""))
                        .with(csrf()))
                .andExpect(status().isAccepted());
        assertThat(contactMessages.count()).isZero();
        assertThat(outbox.count()).isZero();
    }

    @Test
    void rateLimitsPerClient() throws Exception {
        RequestPostProcessor sameClient = uniqueClientIp();
        for (int i = 0; i < 3; i++) {
            mvc.perform(contact(VALID).with(csrf()).with(sameClient)).andExpect(status().isAccepted());
        }
        mvc.perform(contact(VALID).with(csrf()).with(sameClient))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));

        // Another client is not affected.
        mvc.perform(contact(VALID).with(csrf())).andExpect(status().isAccepted());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder contact(String body) {
        return post("/api/v1/contact")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(uniqueClientIp());
    }
}
