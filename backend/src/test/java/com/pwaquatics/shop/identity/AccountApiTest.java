package com.pwaquatics.shop.identity;

import static com.pwaquatics.shop.support.AuthFixture.PASSWORD;
import static com.pwaquatics.shop.support.AuthFixture.json;
import static com.pwaquatics.shop.support.AuthFixture.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pwaquatics.shop.customer.AddressApiTest;
import com.pwaquatics.shop.customer.infrastructure.AddressRepository;
import com.pwaquatics.shop.identity.domain.User;
import com.pwaquatics.shop.identity.domain.UserStatus;
import com.pwaquatics.shop.identity.infrastructure.UserRepository;
import com.pwaquatics.shop.support.AuthFixture;
import com.pwaquatics.shop.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class AccountApiTest {

    @Autowired MockMvc mvc;
    @Autowired AuthFixture auth;
    @Autowired UserRepository users;
    @Autowired AddressRepository addresses;
    @Autowired JdbcTemplate jdbc;

    @Test
    void profileCanBeReadAndUpdated() throws Exception {
        String email = uniqueEmail();
        Cookie session = auth.registeredSession(email);

        mvc.perform(get("/api/v1/account/profile").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.firstName").value("Anna"))
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.locale").value("en"));

        mvc.perform(json(patch("/api/v1/account/profile"), """
                        {"firstName":" Anne ","lastName":"Schmitt","phone":"+49 151 2345678","marketingOptIn":true,"locale":"de"}
                        """).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Anne"))
                .andExpect(jsonPath("$.phone").value("+49 151 2345678"))
                .andExpect(jsonPath("$.marketingOptIn").value(true))
                .andExpect(jsonPath("$.locale").value("de"));

        mvc.perform(json(patch("/api/v1/account/profile"), """
                        {"firstName":"","lastName":"X","phone":"call me","marketingOptIn":true}
                        """).cookie(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givingAndWithdrawingNewsletterConsentIsRecorded() throws Exception {
        String email = uniqueEmail();
        Cookie session = auth.registeredSession(email);
        UUID userId = users.findByEmail(email).orElseThrow().getId();
        String profile = """
                {"firstName":"Anna","lastName":"Schmidt","marketingOptIn":%s}
                """;

        mvc.perform(json(patch("/api/v1/account/profile"), profile.formatted(true)).cookie(session))
                .andExpect(status().isOk());
        // Saving again without a change records nothing new.
        mvc.perform(json(patch("/api/v1/account/profile"), profile.formatted(true)).cookie(session))
                .andExpect(status().isOk());
        mvc.perform(json(patch("/api/v1/account/profile"), profile.formatted(false)).cookie(session))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForList("""
                        SELECT action FROM audit_log
                        WHERE entity_id = ? AND action LIKE 'MARKETING_CONSENT_%' AND ip_address IS NOT NULL
                        ORDER BY id
                        """, String.class, userId))
                .containsExactly("MARKETING_CONSENT_GIVEN", "MARKETING_CONSENT_WITHDRAWN");
    }

    @Test
    void consentGivenAtRegistrationIsRecorded() throws Exception {
        String email = uniqueEmail();
        mvc.perform(json(post("/api/v1/auth/register"), """
                        {"email":"%s","password":"%s","firstName":"Anna","lastName":"Schmidt","marketingOptIn":true}
                        """.formatted(email, PASSWORD)))
                .andExpect(status().isAccepted());
        UUID userId = users.findByEmail(email).orElseThrow().getId();

        assertThat(jdbc.queryForObject(
                        "SELECT details->>'source' FROM audit_log WHERE entity_id = ? AND action = 'MARKETING_CONSENT_GIVEN'",
                        String.class, userId))
                .isEqualTo("registration");
    }

    @Test
    void changingThePasswordNeedsTheCurrentOneAndSignsOutOtherDevices() throws Exception {
        String email = uniqueEmail();
        Cookie laptop = auth.registeredSession(email);
        Cookie phone = auth.login(email, PASSWORD);

        mvc.perform(json(put("/api/v1/account/password"), """
                        {"currentPassword":"wrong-password","newPassword":"a-brand-new-password"}
                        """).cookie(laptop))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));

        mvc.perform(json(put("/api/v1/account/password"), """
                        {"currentPassword":"%s","newPassword":"a-brand-new-password"}
                        """.formatted(PASSWORD)).cookie(laptop))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/auth/me").cookie(laptop)).andExpect(jsonPath("$.authenticated").value(true));
        mvc.perform(get("/api/v1/auth/me").cookie(phone)).andExpect(jsonPath("$.authenticated").value(false));
        assertThat(auth.login(email, "a-brand-new-password")).isNotNull();
        assertThat(auth.emailsTo(email)).last()
                .satisfies(mail -> assertThat(mail.getPayload().subject()).contains("password was changed"));
    }

    @Test
    void passwordConfirmationsAreRateLimited() throws Exception {
        Cookie session = auth.registeredSession(uniqueEmail());
        for (int i = 0; i < 4; i++) {
            mvc.perform(json(put("/api/v1/account/password"), """
                            {"currentPassword":"guess-%d","newPassword":"a-brand-new-password"}
                            """.formatted(i)).cookie(session))
                    .andExpect(status().isUnprocessableContent());
        }
        mvc.perform(json(put("/api/v1/account/password"), """
                        {"currentPassword":"%s","newPassword":"a-brand-new-password"}
                        """.formatted(PASSWORD)).cookie(session))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void emailChangesOnlyAfterTheNewAddressIsConfirmed() throws Exception {
        String oldEmail = uniqueEmail();
        String newEmail = uniqueEmail();
        Cookie session = auth.registeredSession(oldEmail);

        mvc.perform(json(put("/api/v1/account/email"), """
                        {"newEmail":"%s","currentPassword":"%s"}
                        """.formatted(newEmail.toUpperCase(), PASSWORD)).cookie(session))
                .andExpect(status().isAccepted());
        assertThat(users.findByEmail(oldEmail)).isPresent();

        auth.verify(newEmail); // opens the link sent to the new address

        assertThat(users.findByEmail(oldEmail)).isEmpty();
        assertThat(users.findByEmail(newEmail)).isPresent();
        assertThat(auth.emailsTo(oldEmail)).last()
                .satisfies(mail -> assertThat(mail.getPayload().subject()).contains("email address was changed"));
        assertThat(auth.login(newEmail, PASSWORD)).isNotNull();
    }

    @Test
    void emailChangeToATakenAddressSendsNothingAndLooksTheSame() throws Exception {
        String taken = uniqueEmail();
        auth.registeredSession(taken);
        int emailsBefore = auth.emailsTo(taken).size();
        Cookie session = auth.registeredSession(uniqueEmail());

        mvc.perform(json(put("/api/v1/account/email"), """
                        {"newEmail":"%s","currentPassword":"%s"}
                        """.formatted(taken, PASSWORD)).cookie(session))
                .andExpect(status().isAccepted());
        assertThat(auth.emailsTo(taken)).hasSize(emailsBefore);
    }

    @Test
    void deletingTheAccountRemovesPersonalDataAndEndsTheSession() throws Exception {
        String email = uniqueEmail();
        Cookie session = auth.registeredSession(email);
        UUID userId = users.findByEmail(email).orElseThrow().getId();
        mvc.perform(json(post("/api/v1/account/addresses"), AddressApiTest.ADDRESS).cookie(session))
                .andExpect(status().isCreated());

        mvc.perform(json(delete("/api/v1/account"), "{\"currentPassword\":\"nope-nope-nope\"}").cookie(session))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(json(delete("/api/v1/account"), "{\"currentPassword\":\"%s\"}".formatted(PASSWORD)).cookie(session))
                .andExpect(status().isNoContent());

        User deleted = users.findById(userId).orElseThrow();
        assertThat(deleted.getStatus()).isEqualTo(UserStatus.ANONYMISED);
        assertThat(deleted.getEmail()).doesNotContain(email).endsWith("@anonymised.invalid");
        assertThat(deleted.getPasswordHash()).isNull();
        assertThat(deleted.getFirstName()).isEqualTo("Deleted");
        assertThat(addresses.countByUserId(userId)).isZero();

        mvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(jsonPath("$.authenticated").value(false));
        mvc.perform(json(post("/api/v1/auth/login"), """
                        {"email":"%s","password":"%s"}
                        """.formatted(email, PASSWORD)))
                .andExpect(status().isUnauthorized());
        // The address is free again for a new registration.
        auth.register(email);
        assertThat(users.findByEmail(email)).isPresent();
    }
}
