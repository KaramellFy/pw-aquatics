package com.pwaquatics.shop.identity;

import static com.pwaquatics.shop.support.AuthFixture.PASSWORD;
import static com.pwaquatics.shop.support.AuthFixture.json;
import static com.pwaquatics.shop.support.AuthFixture.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pwaquatics.shop.common.crypto.Base32;
import com.pwaquatics.shop.common.crypto.SecretBox;
import com.pwaquatics.shop.identity.domain.Totp;
import com.pwaquatics.shop.notification.domain.OutboxMessage;
import com.pwaquatics.shop.support.AuthFixture;
import com.pwaquatics.shop.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Two-step sign-in for administrators (ADR-015, docs/API.md §2.1 and §2.6). */
@IntegrationTest
class TwoFactorApiTest {

    private static final String ADMIN_AREA = "/api/v1/admin/orders";
    private static final String TWO_FACTOR = "/api/v1/account/two-factor";

    @Autowired MockMvc mvc;
    @Autowired AuthFixture auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired SecretBox secretBox;

    @Test
    void anAdministratorWithoutTheSecondStepIsSignedInButKeptOutOfTheAdminArea() throws Exception {
        String email = uniqueEmail();
        Cookie session = auth.adminWithoutTwoFactor(email);

        mvc.perform(get("/api/v1/auth/me").cookie(session))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.twoFactorSetupRequired").value(true));
        mvc.perform(get(ADMIN_AREA).cookie(session)).andExpect(status().isForbidden());
        mvc.perform(get(TWO_FACTOR).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.recoveryCodesLeft").value(0));

        Cookie upgraded = auth.setUpTwoFactor(email, session);

        assertThat(upgraded.getValue()).isNotEqualTo(session.getValue());
        mvc.perform(get("/api/v1/auth/me").cookie(upgraded))
                .andExpect(jsonPath("$.twoFactorSetupRequired").value(false));
        mvc.perform(get(ADMIN_AREA).cookie(upgraded)).andExpect(status().isOk());
        mvc.perform(get(TWO_FACTOR).cookie(upgraded))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.enabledAt").isNotEmpty())
                .andExpect(jsonPath("$.recoveryCodesLeft").value(10));
        assertThat(actions(email)).contains("USER_TWO_FACTOR_ENABLED");
        assertThat(subjects(email)).contains("Two-step sign-in is on");
    }

    @Test
    void setupNeedsTheFirstCodeFromTheNewKey() throws Exception {
        String email = uniqueEmail();
        Cookie session = auth.adminWithoutTwoFactor(email);

        mvc.perform(json(post(TWO_FACTOR + "/confirm"), code("123456")).cookie(session))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_SETUP_NOT_STARTED"));
        String setup = mvc.perform(json(post(TWO_FACTOR + "/setup"), "{}").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otpauthUri").value(org.hamcrest.Matchers.startsWith(
                        "otpauth://totp/PW%20Aquatics:")))
                .andReturn().getResponse().getContentAsString();
        String key = JsonPath.read(setup, "$.secret");
        assertThat(JsonPath.<String>read(setup, "$.otpauthUri")).contains("secret=" + key).contains("issuer=PW%20Aquatics");

        mvc.perform(json(post(TWO_FACTOR + "/confirm"), code(wrongCode(key))).cookie(session))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CODE_INVALID"));
        mvc.perform(get(ADMIN_AREA).cookie(session)).andExpect(status().isForbidden());

        // The key is stored encrypted and bound to its account.
        String stored = jdbc.queryForObject(
                "SELECT pending_secret_ciphertext FROM user_two_factor WHERE user_id = ?", String.class, userId(email));
        assertThat(stored).doesNotContain(key);
        assertThat(Base32.encode(secretBox.open(stored, "two-factor:" + userId(email)))).isEqualTo(key);
    }

    @Test
    void signingInAsksForTheCodeAfterThePasswordAndGrantsNothingBefore() throws Exception {
        String email = uniqueEmail();
        auth.adminSession(email);

        Cookie pending = mvc.perform(json(post("/api/v1/auth/login"), login(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.twoFactorRequired").value(true))
                .andReturn().getResponse().getCookie("SESSION");
        mvc.perform(get("/api/v1/auth/me").cookie(pending))
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.twoFactorRequired").value(true));
        mvc.perform(get(ADMIN_AREA).cookie(pending)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/account/profile").cookie(pending)).andExpect(status().isUnauthorized());

        mvc.perform(json(post("/api/v1/auth/login/two-factor"), code(wrongCode(auth.appKey(email)))).cookie(pending))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CODE_INVALID"));
        String used = auth.nextCode(email);
        var signedIn = mvc.perform(json(post("/api/v1/auth/login/two-factor"), code(used)).cookie(pending))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.twoFactorSetupRequired").value(false))
                .andReturn().getResponse();
        Cookie session = signedIn.getCookie("SESSION");
        assertThat(session.getValue()).isNotEqualTo(pending.getValue());
        mvc.perform(get(ADMIN_AREA).cookie(session)).andExpect(status().isOk());
        assertThat(actions(email)).contains("USER_TWO_FACTOR_CODE_REJECTED");

        // The same code does not work a second time.
        Cookie again = mvc.perform(json(post("/api/v1/auth/login"), login(email)))
                .andReturn().getResponse().getCookie("SESSION");
        mvc.perform(json(post("/api/v1/auth/login/two-factor"), code(used)).cookie(again))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CODE_INVALID"));
    }

    @Test
    void theSecondStepNeedsAPendingSignIn() throws Exception {
        mvc.perform(json(post("/api/v1/auth/login/two-factor"), code("123456")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_EXPIRED"));
        mvc.perform(json(post("/api/v1/auth/login/two-factor"), code("")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/login/two-factor")
                        .contentType("application/json").content(code("123456")))
                .andExpect(status().isForbidden()); // no CSRF token
    }

    @Test
    void aRecoveryCodeWorksOnceAndIsAnnounced() throws Exception {
        String email = uniqueEmail();
        Cookie session = auth.adminWithoutTwoFactor(email);
        List<String> codes = setUpAndKeepCodes(email, session);

        Cookie pending = startSignIn(email);
        mvc.perform(json(post("/api/v1/auth/login/two-factor"), code(codes.getFirst().toUpperCase())).cookie(pending))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));

        Cookie other = startSignIn(email);
        mvc.perform(json(post("/api/v1/auth/login/two-factor"), code(codes.getFirst())).cookie(other))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CODE_INVALID"));
        assertThat(actions(email)).contains("USER_TWO_FACTOR_RECOVERY_CODE_USED");
        assertThat(subjects(email)).contains("A recovery code was used");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM two_factor_recovery_codes WHERE user_id = ? AND used_at IS NULL",
                        Integer.class, userId(email)))
                .isEqualTo(9);
        // Only hashes are stored.
        assertThat(jdbc.queryForList("SELECT code_hash FROM two_factor_recovery_codes WHERE user_id = ?",
                        String.class, userId(email)))
                .noneMatch(hash -> codes.stream().anyMatch(c -> hash.contains(c.replace("-", ""))));
    }

    @Test
    void tooManyWrongCodesEndTheSignInAndWarnTheAdministrator() throws Exception {
        String email = uniqueEmail();
        auth.adminSession(email);
        Cookie pending = startSignIn(email);
        String wrong = wrongCode(auth.appKey(email));

        for (int i = 1; i < 5; i++) {
            mvc.perform(json(post("/api/v1/auth/login/two-factor"), code(wrong)).cookie(pending))
                    .andExpect(jsonPath("$.code").value("TWO_FACTOR_CODE_INVALID"));
        }
        mvc.perform(json(post("/api/v1/auth/login/two-factor"), code(wrong)).cookie(pending))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_EXPIRED"));
        mvc.perform(json(post("/api/v1/auth/login/two-factor"), code(auth.nextCode(email))).cookie(pending))
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_EXPIRED"));
        assertThat(subjects(email)).contains("Several wrong sign-in codes");
        assertThat(actions(email)).contains("USER_TWO_FACTOR_TOO_MANY_WRONG_CODES");

        // The checks are also limited per account across sign-ins (8 in the test profile; setup took one).
        Cookie next = startSignIn(email);
        mvc.perform(json(post("/api/v1/auth/login/two-factor"), code(wrong)).cookie(next))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/v1/auth/login/two-factor"), code(wrong)).cookie(next))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/v1/auth/login/two-factor"), code(auth.nextCode(email))).cookie(next))
                .andExpect(status().isTooManyRequests());
    }

    /**
     * Codes from the app can be used once per 30 seconds; where a test needs more within one step,
     * it uses recovery codes instead (as an administrator whose phone is lost would).
     */
    @Test
    void replacingTheAppNeedsACurrentCodeAndSignsOutOtherSessions() throws Exception {
        String email = uniqueEmail();
        Cookie session = auth.adminWithoutTwoFactor(email);
        List<String> codes = setUpAndKeepCodes(email, session);
        String oldKey = auth.appKey(email);
        Cookie first = auth.adminLogin(email);
        Cookie second = startSignIn(email);
        second = mvc.perform(json(post("/api/v1/auth/login/two-factor"), code(codes.get(0))).cookie(second))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SESSION");

        mvc.perform(json(post(TWO_FACTOR + "/setup"), "{}").cookie(first))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CODE_REQUIRED"));
        mvc.perform(json(post(TWO_FACTOR + "/setup"), code(wrongCode(oldKey))).cookie(first))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CODE_INVALID"));
        String setup = mvc.perform(json(post(TWO_FACTOR + "/setup"), code(codes.get(1))).cookie(first))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newKey = JsonPath.read(setup, "$.secret");

        // Until the new app is confirmed, the old one still signs in.
        mvc.perform(get(ADMIN_AREA).cookie(second)).andExpect(status().isOk());
        var confirmed = mvc.perform(json(post(TWO_FACTOR + "/confirm"),
                        code(Totp.code(Base32.decode(newKey), Totp.step(Instant.now())))).cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes.length()").value(10))
                .andReturn().getResponse();
        Cookie current = confirmed.getCookie("SESSION");

        mvc.perform(get(ADMIN_AREA).cookie(current)).andExpect(status().isOk());
        mvc.perform(get(ADMIN_AREA).cookie(second)).andExpect(status().isUnauthorized());
        // The old app no longer works, nor do the old recovery codes.
        Cookie pending = startSignIn(email);
        mvc.perform(json(post("/api/v1/auth/login/two-factor"),
                        code(Totp.code(Base32.decode(oldKey), Totp.step(Instant.now())))).cookie(pending))
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CODE_INVALID"));
        mvc.perform(json(post("/api/v1/auth/login/two-factor"), code(codes.get(2))).cookie(pending))
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CODE_INVALID"));
        assertThat(actions(email)).contains("USER_TWO_FACTOR_APP_CHANGED");
        assertThat(subjects(email)).contains("New app for two-step sign-in");
    }

    @Test
    void newRecoveryCodesReplaceTheOldOnes() throws Exception {
        String email = uniqueEmail();
        Cookie session = auth.adminWithoutTwoFactor(email);
        List<String> old = setUpAndKeepCodes(email, session);
        Cookie admin = auth.adminLogin(email);

        mvc.perform(json(post(TWO_FACTOR + "/recovery-codes"), code("000000")).cookie(admin))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(json(post(TWO_FACTOR + "/recovery-codes"), code(old.get(0))).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes.length()").value(10));

        Cookie pending = startSignIn(email);
        mvc.perform(json(post("/api/v1/auth/login/two-factor"), code(old.get(1))).cookie(pending))
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CODE_INVALID"));
        assertThat(actions(email)).contains("USER_TWO_FACTOR_RECOVERY_CODES_RENEWED");
    }

    @Test
    void customersAndVisitorsCannotUseIt() throws Exception {
        Cookie customer = auth.registeredSession(uniqueEmail());

        mvc.perform(get(TWO_FACTOR).cookie(customer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_ADMINS_ONLY"));
        mvc.perform(json(post(TWO_FACTOR + "/setup"), "{}").cookie(customer))
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_ADMINS_ONLY"));
        mvc.perform(get(TWO_FACTOR)).andExpect(status().isUnauthorized());
        mvc.perform(json(post(TWO_FACTOR + "/setup"), "{}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").cookie(customer))
                .andExpect(jsonPath("$.twoFactorSetupRequired").value(false));
    }

    @Test
    void deletingTheAccountRemovesTheSecondStep() throws Exception {
        String email = uniqueEmail();
        Cookie admin = auth.adminSession(email);
        UUID id = userId(email);

        mvc.perform(json(delete("/api/v1/account"), "{\"currentPassword\":\"%s\"}".formatted(PASSWORD)).cookie(admin))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_two_factor WHERE user_id = ?", Integer.class, id))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM two_factor_recovery_codes WHERE user_id = ?", Integer.class, id))
                .isZero();
    }

    private List<String> setUpAndKeepCodes(String email, Cookie session) throws Exception {
        String setup = mvc.perform(json(post(TWO_FACTOR + "/setup"), "{}").cookie(session))
                .andReturn().getResponse().getContentAsString();
        String key = JsonPath.read(setup, "$.secret");
        String confirmed = confirm(session, Totp.code(Base32.decode(key), Totp.step(Instant.now())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // Lets the fixture sign in with this app later.
        auth.rememberAppKey(email, key);
        return JsonPath.read(confirmed, "$.recoveryCodes");
    }

    private ResultActions confirm(Cookie session, String code) throws Exception {
        return mvc.perform(json(post(TWO_FACTOR + "/confirm"), code(code)).cookie(session));
    }

    private Cookie startSignIn(String email) throws Exception {
        return mvc.perform(json(post("/api/v1/auth/login"), login(email)))
                .andExpect(jsonPath("$.twoFactorRequired").value(true))
                .andReturn().getResponse().getCookie("SESSION");
    }

    private static String login(String email) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD);
    }

    private static String code(String code) {
        return "{\"code\":\"%s\"}".formatted(code);
    }

    /** A six-digit code that is none of the ones the app would accept now. */
    private static String wrongCode(String key) {
        byte[] secret = Base32.decode(key);
        long step = Totp.step(Instant.now());
        List<String> valid = List.of(
                Totp.code(secret, step - 1), Totp.code(secret, step), Totp.code(secret, step + 1));
        for (int candidate = 0; ; candidate++) {
            String code = "%06d".formatted(candidate);
            if (!valid.contains(code)) {
                return code;
            }
        }
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?::citext", UUID.class, email);
    }

    private List<String> actions(String email) {
        return jdbc.queryForList("SELECT action FROM audit_log WHERE actor_user_id = ? ORDER BY id",
                String.class, userId(email));
    }

    private List<String> subjects(String email) {
        return auth.emailsTo(email).stream().map(OutboxMessage::getPayload).map(p -> p.subject()).toList();
    }
}
