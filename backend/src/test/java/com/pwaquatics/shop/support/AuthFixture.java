package com.pwaquatics.shop.support;

import static com.pwaquatics.shop.support.TestRequests.uniqueClientIp;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pwaquatics.shop.common.crypto.Base32;
import com.pwaquatics.shop.identity.domain.Totp;
import com.pwaquatics.shop.notification.domain.OutboxMessage;
import com.pwaquatics.shop.notification.infrastructure.OutboxMessageRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Drives the real auth endpoints for tests: register, verify by emailed token, log in. */
@TestComponent
public class AuthFixture {

    public static final String PASSWORD = "correct-horse-battery";
    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    private final MockMvc mvc;
    private final OutboxMessageRepository outbox;
    private final JdbcTemplate jdbc;
    /** The authenticator-app keys of administrators set up by this fixture, by email. */
    private final Map<String, String> appKeys = new ConcurrentHashMap<>();

    public AuthFixture(MockMvc mvc, OutboxMessageRepository outbox, JdbcTemplate jdbc) {
        this.mvc = mvc;
        this.outbox = outbox;
        this.jdbc = jdbc;
    }

    public static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    public static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body).with(csrf()).with(uniqueClientIp());
    }

    public void register(String email) throws Exception {
        mvc.perform(json(post("/api/v1/auth/register"), """
                        {"email":"%s","password":"%s","firstName":"Anna","lastName":"Schmidt"}
                        """.formatted(email, PASSWORD)))
                .andExpect(status().isAccepted());
    }

    public void verify(String email) throws Exception {
        mvc.perform(json(post("/api/v1/auth/verify-email"), "{\"token\":\"%s\"}".formatted(latestToken(email))))
                .andExpect(status().isNoContent());
    }

    /** Registers and verifies a new customer and returns the session cookie of a fresh login. */
    public Cookie registeredSession(String email) throws Exception {
        register(email);
        verify(email);
        return login(email, PASSWORD);
    }

    /**
     * A verified account promoted to administrator, signed in with both steps: the password, then
     * two-step sign-in set up through the real endpoints (the admin area needs both).
     */
    public Cookie adminSession(String email) throws Exception {
        return setUpTwoFactor(email, adminWithoutTwoFactor(email));
    }

    /** An administrator signed in with the password only, before setting up two-step sign-in. */
    public Cookie adminWithoutTwoFactor(String email) throws Exception {
        register(email);
        verify(email);
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ?::citext", email);
        return login(email, PASSWORD);
    }

    /** Sets up the authenticator app for a signed-in administrator; returns the upgraded session. */
    public Cookie setUpTwoFactor(String email, Cookie session) throws Exception {
        String setup = mvc.perform(json(post("/api/v1/account/two-factor/setup"), "{}").cookie(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String key = JsonPath.read(setup, "$.secret");
        MvcResult confirmed = mvc.perform(json(post("/api/v1/account/two-factor/confirm"),
                                "{\"code\":\"%s\"}".formatted(Totp.code(Base32.decode(key), Totp.step(Instant.now()))))
                        .cookie(session))
                .andExpect(status().isOk())
                .andReturn();
        appKeys.put(email.toLowerCase(), key);
        return sessionCookie(confirmed, session);
    }

    /** Signs an administrator in with password and a fresh code from the app. */
    public Cookie adminLogin(String email) throws Exception {
        MvcResult password = mvc.perform(json(post("/api/v1/auth/login"), """
                        {"email":"%s","password":"%s"}
                        """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie pending = password.getResponse().getCookie("SESSION");
        MvcResult done = mvc.perform(json(post("/api/v1/auth/login/two-factor"),
                                "{\"code\":\"%s\"}".formatted(nextCode(email)))
                        .cookie(pending))
                .andExpect(status().isOk())
                .andReturn();
        return sessionCookie(done, pending);
    }

    /** A code the app would show that has not been used yet (each code is accepted once). */
    public String nextCode(String email) {
        String key = appKeys.get(email.toLowerCase());
        if (key == null) {
            throw new AssertionError("No authenticator app set up by this fixture for " + email);
        }
        Long lastUsed = jdbc.queryForObject("""
                SELECT t.last_used_step FROM user_two_factor t JOIN users u ON u.id = t.user_id
                WHERE u.email = ?::citext""", Long.class, email);
        long step = Math.max(Totp.step(Instant.now()), lastUsed == null ? 0 : lastUsed + 1);
        return Totp.code(Base32.decode(key), step);
    }

    /** Lets {@link #nextCode} and {@link #adminLogin} work for an app a test set up itself. */
    public void rememberAppKey(String email, String key) {
        appKeys.put(email.toLowerCase(), key);
    }

    /** The app key of an administrator set up by this fixture (Base32). */
    public String appKey(String email) {
        return appKeys.get(email.toLowerCase());
    }

    /** The session cookie a response set (the session id changes on sign-in), else the one sent. */
    private static Cookie sessionCookie(MvcResult result, Cookie sent) {
        Cookie renewed = result.getResponse().getCookie("SESSION");
        return renewed != null ? renewed : sent;
    }

    public Cookie login(String email, String password) throws Exception {
        return mvc.perform(json(post("/api/v1/auth/login"), """
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie("SESSION");
    }

    /** Emails queued for an address, oldest first. */
    public List<OutboxMessage> emailsTo(String email) {
        return outbox.findAll().stream()
                .filter(message -> message.getPayload().to().equalsIgnoreCase(email))
                .sorted(Comparator.comparing(OutboxMessage::getId))
                .toList();
    }

    /** The token from the most recent emailed link to this address. */
    public String latestToken(String email) {
        List<OutboxMessage> emails = emailsTo(email);
        for (int i = emails.size() - 1; i >= 0; i--) {
            Matcher matcher = TOKEN.matcher(emails.get(i).getPayload().body());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new AssertionError("No emailed link found for " + email);
    }
}
