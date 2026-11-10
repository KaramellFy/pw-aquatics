package com.pwaquatics.shop.identity;

import static com.pwaquatics.shop.support.AuthFixture.PASSWORD;
import static com.pwaquatics.shop.support.AuthFixture.json;
import static com.pwaquatics.shop.support.AuthFixture.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pwaquatics.shop.audit.infrastructure.AuditLogRepository;
import com.pwaquatics.shop.identity.domain.User;
import com.pwaquatics.shop.identity.infrastructure.UserRepository;
import com.pwaquatics.shop.support.AuthFixture;
import com.pwaquatics.shop.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class AuthApiTest {

    @Autowired MockMvc mvc;
    @Autowired AuthFixture auth;
    @Autowired UserRepository users;
    @Autowired AuditLogRepository auditLog;

    @Test
    void registrationRequiresEmailVerificationBeforeLogin() throws Exception {
        String email = uniqueEmail();
        auth.register(email);

        User user = users.findByEmail(email).orElseThrow();
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getPasswordHash()).startsWith("{argon2}").doesNotContain(PASSWORD);
        assertThat(auth.emailsTo(email)).singleElement()
                .satisfies(mail -> assertThat(mail.getPayload().body()).contains("/verify-email?token="));

        login(email, PASSWORD).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        auth.verify(email);
        login(email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void verificationLinksWorkOnlyOnce() throws Exception {
        String email = uniqueEmail();
        auth.register(email);
        String token = auth.latestToken(email);
        auth.verify(email);

        mvc.perform(json(post("/api/v1/auth/verify-email"), "{\"token\":\"%s\"}".formatted(token)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        mvc.perform(json(post("/api/v1/auth/verify-email"), "{\"token\":\"made-up\"}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void loginSetsAHardenedSessionCookieAndMeReflectsTheSession() throws Exception {
        String email = uniqueEmail();
        auth.register(email);
        auth.verify(email);

        MvcResult result = login(email.toUpperCase(), PASSWORD).andExpect(status().isOk()).andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        assertThat(session.isHttpOnly()).isTrue();
        assertThat(session.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(session.getPath()).isEqualTo("/");

        mvc.perform(get("/api/v1/auth/me").cookie(session))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.firstName").value("Anna"));
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.user").doesNotExist());
        assertThat(users.findByEmail(email).orElseThrow().getLastLoginAt()).isNotNull();
    }

    @Test
    void loginIssuesANewSessionIdAndLogoutEndsTheSession() throws Exception {
        String email = uniqueEmail();
        Cookie first = auth.registeredSession(email);

        // Logging in again while presenting the old cookie must not keep the old session id (fixation).
        Cookie second = mvc.perform(json(post("/api/v1/auth/login"), credentials(email, PASSWORD)).cookie(first))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie("SESSION");
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        mvc.perform(post("/api/v1/auth/logout").cookie(second).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").cookie(second)).andExpect(jsonPath("$.authenticated").value(false));
        // Logging out without a session is harmless.
        mvc.perform(post("/api/v1/auth/logout").with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    void unknownAddressAndWrongPasswordLookTheSame() throws Exception {
        String email = uniqueEmail();
        auth.register(email);
        auth.verify(email);

        String wrongPassword = login(email, "not-the-password").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownUser = login(uniqueEmail(), "not-the-password").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(strip(wrongPassword)).isEqualTo(strip(unknownUser)).contains("INVALID_CREDENTIALS");
    }

    @Test
    void registeringAnExistingAddressRevealsNothingAndKeepsThePassword() throws Exception {
        String email = uniqueEmail();
        auth.register(email);
        auth.verify(email);

        mvc.perform(json(post("/api/v1/auth/register"), """
                        {"email":"%s","password":"attacker-password-1","firstName":"Eve","lastName":"X"}
                        """.formatted(email.toUpperCase())))
                .andExpect(status().isAccepted());

        assertThat(users.findAll().stream().filter(u -> u.getEmail().equals(email))).hasSize(1);
        assertThat(auth.emailsTo(email)).last()
                .satisfies(mail -> assertThat(mail.getPayload().subject()).contains("already have"));
        login(email, "attacker-password-1").andExpect(status().isUnauthorized());
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void repeatedWrongPasswordsLockTheAccountTemporarily() throws Exception {
        String email = uniqueEmail();
        auth.register(email);
        auth.verify(email);

        for (int i = 0; i < 3; i++) {
            login(email, "wrong-password-" + i).andExpect(status().isUnauthorized());
        }
        // Even the correct password is refused while the lock lasts.
        login(email, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
        assertThat(auditLog.findByActionOrderByIdAsc("USER_LOCKED_AFTER_FAILED_LOGINS"))
                .anySatisfy(entry -> assertThat(entry.getEntityId())
                        .isEqualTo(users.findByEmail(email).orElseThrow().getId()));
    }

    @Test
    void attemptsAreLimitedPerAddressEvenForUnknownAccounts() throws Exception {
        String email = uniqueEmail();
        for (int i = 0; i < 8; i++) {
            login(email, "some-password").andExpect(status().isUnauthorized());
        }
        login(email, "some-password").andExpect(status().isTooManyRequests());
    }

    @Test
    void passwordResetChangesThePasswordAndEndsAllSessions() throws Exception {
        String email = uniqueEmail();
        Cookie session = auth.registeredSession(email);

        // Unknown addresses get the same answer and no email.
        String unknown = uniqueEmail();
        mvc.perform(json(post("/api/v1/auth/forgot-password"), "{\"email\":\"%s\"}".formatted(unknown)))
                .andExpect(status().isAccepted());
        assertThat(auth.emailsTo(unknown)).isEmpty();

        mvc.perform(json(post("/api/v1/auth/forgot-password"), "{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isAccepted());
        String token = auth.latestToken(email);

        mvc.perform(json(post("/api/v1/auth/reset-password"),
                        "{\"token\":\"%s\",\"newPassword\":\"short\"}".formatted(token)))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/v1/auth/reset-password"),
                        "{\"token\":\"%s\",\"newPassword\":\"a-brand-new-password\"}".formatted(token)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(jsonPath("$.authenticated").value(false));
        login(email, PASSWORD).andExpect(status().isUnauthorized());
        login(email, "a-brand-new-password").andExpect(status().isOk());
        // The link cannot be reused.
        mvc.perform(json(post("/api/v1/auth/reset-password"),
                        "{\"token\":\"%s\",\"newPassword\":\"yet-another-password\"}".formatted(token)))
                .andExpect(status().isUnprocessableContent());
        assertThat(auth.emailsTo(email)).last()
                .satisfies(mail -> assertThat(mail.getPayload().subject()).contains("password was changed"));
    }

    @Test
    void weakOrInvalidRegistrationsAreRejected() throws Exception {
        mvc.perform(json(post("/api/v1/auth/register"), """
                        {"email":"nope","password":"short","firstName":"","lastName":"X"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'password')].code").value("SIZE"));
        mvc.perform(json(post("/api/v1/auth/register"), """
                        {"email":"%s","password":"1234567890","firstName":"A","lastName":"B"}
                        """.formatted(uniqueEmail())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_WEAK"));
    }

    @Test
    void stateChangingAuthRequestsNeedACsrfToken() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(credentials("a@b.de", "x")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void protectedAreasRequireLoginAndTheRightRole() throws Exception {
        mvc.perform(get("/api/v1/account/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/admin/orders")).andExpect(status().isUnauthorized());

        Cookie customer = auth.registeredSession(uniqueEmail());
        mvc.perform(get("/api/v1/admin/orders").cookie(customer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(json(post("/api/v1/auth/login"), credentials(email, password)));
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    /** Removes the per-request trace id so two error bodies can be compared. */
    private static String strip(String body) {
        return body.replaceAll("\"traceId\":\"[^\"]+\"", "");
    }
}
