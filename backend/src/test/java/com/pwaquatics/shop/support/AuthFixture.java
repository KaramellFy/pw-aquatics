package com.pwaquatics.shop.support;

import static com.pwaquatics.shop.support.TestRequests.uniqueClientIp;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pwaquatics.shop.notification.domain.OutboxMessage;
import com.pwaquatics.shop.notification.infrastructure.OutboxMessageRepository;
import jakarta.servlet.http.Cookie;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Drives the real auth endpoints for tests: register, verify by emailed token, log in. */
@TestComponent
public class AuthFixture {

    public static final String PASSWORD = "correct-horse-battery";
    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    private final MockMvc mvc;
    private final OutboxMessageRepository outbox;
    private final JdbcTemplate jdbc;

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

    /** A verified account promoted to administrator, signed in (the role is read at login). */
    public Cookie adminSession(String email) throws Exception {
        register(email);
        verify(email);
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ?::citext", email);
        return login(email, PASSWORD);
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
