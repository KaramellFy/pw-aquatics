package com.pwaquatics.shop.identity;

import static com.pwaquatics.shop.support.AuthFixture.json;
import static com.pwaquatics.shop.support.AuthFixture.uniqueEmail;
import static com.pwaquatics.shop.support.FakeGoogle.person;
import static com.pwaquatics.shop.support.TestRequests.uniqueClientIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pwaquatics.shop.support.AuthFixture;
import com.pwaquatics.shop.support.FakeGoogle;
import com.pwaquatics.shop.support.FakeGoogle.Person;
import com.pwaquatics.shop.support.FakeGoogle.Tamper;
import com.pwaquatics.shop.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/** Sign in with Google against a local stand-in for Google (docs/adr/014-sign-in-with-google.md). */
@IntegrationTest
class GoogleSignInApiTest {

    @Autowired MockMvc mvc;
    @Autowired AuthFixture auth;
    @Autowired FakeGoogle google;
    @Autowired JdbcTemplate jdbc;

    /** The browser after a round trip through Google: where it was sent and its session cookie. */
    private record Arrival(String location, Cookie session) {}

    @Test
    void theShopSaysWhetherGoogleIsOffered() throws Exception {
        mvc.perform(get("/api/v1/auth/sign-in-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.google").value(true));
    }

    @Test
    void aNewCustomerGetsAnAccountWithoutPasswordAndIsSignedIn() throws Exception {
        String email = uniqueEmail();
        MockHttpServletResponse start = start("/checkout", null);

        // Off to Google with state, nonce and PKCE, asking only for the basic profile.
        String location = start.getHeader("Location");
        assertThat(location).startsWith(google.authorizationEndpoint());
        Map<String, String> query = FakeGoogle.query(location.substring(location.indexOf('?') + 1));
        assertThat(query).containsKeys("state", "nonce", "code_challenge")
                .containsEntry("code_challenge_method", "S256")
                .containsEntry("response_type", "code")
                .containsEntry("client_id", FakeGoogle.CLIENT_ID)
                .containsEntry("hl", "en")
                .containsEntry("redirect_uri", "http://localhost:5173/api/v1/auth/google/callback");
        assertThat(query.get("scope").split(" ")).containsExactlyInAnyOrder("openid", "email", "profile");

        Arrival arrival = callback(google.approve(location, person(email)), start.getCookie("SESSION"));
        assertThat(arrival.location()).isEqualTo("/checkout");

        mvc.perform(get("/api/v1/auth/me").cookie(arrival.session()))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.firstName").value("Grace"));
        mvc.perform(get("/api/v1/account/profile").cookie(arrival.session()))
                .andExpect(jsonPath("$.hasPassword").value(false))
                .andExpect(jsonPath("$.googleEmail").value(email))
                .andExpect(jsonPath("$.locale").value("en"))
                .andExpect(jsonPath("$.confirmedUntil").doesNotExist());
        // Nothing to confirm: Google has verified the address.
        assertThat(auth.emailsTo(email)).isEmpty();
    }

    @Test
    void germanPagesStayGermanAtGoogleAndInTheNewAccount() throws Exception {
        String email = uniqueEmail();
        MockHttpServletResponse start = start("/de/konto", null);
        assertThat(start.getHeader("Location")).contains("hl=de");

        Arrival arrival = callback(google.approve(start.getHeader("Location"), person(email)), start.getCookie("SESSION"));
        assertThat(arrival.location()).isEqualTo("/de/konto");
        mvc.perform(get("/api/v1/account/profile").cookie(arrival.session()))
                .andExpect(jsonPath("$.locale").value("de"));
    }

    @Test
    void anExistingAccountWithTheSameVerifiedAddressIsConnectedAndTold() throws Exception {
        String email = uniqueEmail();
        auth.registeredSession(email);
        Person grace = person(email.toUpperCase());

        Arrival arrival = signIn(grace, "/account");
        mvc.perform(get("/api/v1/account/profile").cookie(arrival.session()))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.googleEmail").value(email.toUpperCase()));
        assertThat(auth.emailsTo(email).getLast().getPayload().subject()).isEqualTo("Sign-in with Google added");
        // The password still works, and the next Google sign-in finds the account by Google's id
        // even after the address at Google changed.
        auth.login(email, AuthFixture.PASSWORD);
        Arrival again = signIn(grace.withEmail("grace@elsewhere.example"), "/account");
        mvc.perform(get("/api/v1/auth/me").cookie(again.session())).andExpect(jsonPath("$.user.email").value(email));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?::citext", Integer.class,
                "grace@elsewhere.example")).isZero();
    }

    @Test
    void aPasswordSetBeforeTheAddressWasVerifiedIsRemovedWhenGoogleProvesTheAddress() throws Exception {
        // Someone registered the address of another person (it was never confirmed) ...
        String email = uniqueEmail();
        auth.register(email);

        // ... then its real owner signs in with Google: the stranger's password must not open the account.
        Arrival arrival = signIn(person(email), "/account");
        mvc.perform(get("/api/v1/account/profile").cookie(arrival.session()))
                .andExpect(jsonPath("$.hasPassword").value(false));
        mvc.perform(json(post("/api/v1/auth/login"), """
                        {"email":"%s","password":"%s"}
                        """.formatted(email, AuthFixture.PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusalsAreReportedOnTheSignInPage() throws Exception {
        String unverified = uniqueEmail();
        Person notVerified = new Person("sub-" + unverified, unverified, false, "Ada", "Lovelace");
        assertThat(signInRefused(notVerified, "/de/kasse")).isEqualTo("/de/login?redirect=%2Fkasse&google=unverified");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?::citext", Integer.class, unverified))
                .isZero();

        String admin = uniqueEmail();
        auth.adminSession(admin);
        assertThat(signInRefused(person(admin), "/account")).isEqualTo("/login?redirect=%2Faccount&google=password_only");

        String locked = uniqueEmail();
        auth.registeredSession(locked);
        jdbc.update("UPDATE users SET status = 'LOCKED' WHERE email = ?::citext", locked);
        assertThat(signInRefused(person(locked), "/account")).isEqualTo("/login?redirect=%2Faccount&google=locked");

        // An account that is already connected to another Google account is not taken over.
        String taken = uniqueEmail();
        signIn(person(taken), "/account");
        assertThat(signInRefused(person(taken), "/account")).endsWith("google=other_account");
    }

    @Test
    void cancellingAtGoogleOrComingBackWithoutAStartedFlowSignsNobodyIn() throws Exception {
        MockHttpServletResponse start = start("/checkout", null);
        String state = FakeGoogle.query(start.getHeader("Location").split("\\?", 2)[1]).get("state");
        MockHttpServletResponse cancelled = mvc.perform(get("/api/v1/auth/google/callback?error=access_denied&state=" + state)
                        .cookie(start.getCookie("SESSION")).with(uniqueClientIp()))
                .andExpect(status().isFound()).andReturn().getResponse();
        assertThat(cancelled.getHeader("Location")).isEqualTo("/login?redirect=%2Fcheckout&google=cancelled");

        // A code meant for another browser (no flow in this session, or a different state) is ignored.
        int exchangesBefore = google.tokenRequests();
        MockHttpServletResponse other = start("/checkout", null);
        String answer = google.approve(other.getHeader("Location"), person(uniqueEmail()));
        mvc.perform(get("/api/v1/auth/google/callback?" + answer).with(uniqueClientIp()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/login?redirect=%2Faccount&google=expired"));
        MockHttpServletResponse mine = start("/checkout", null);
        mvc.perform(get("/api/v1/auth/google/callback?" + answer).cookie(mine.getCookie("SESSION")).with(uniqueClientIp()))
                .andExpect(header().string("Location", "/login?redirect=%2Faccount&google=expired"));
        assertThat(google.tokenRequests()).isEqualTo(exchangesBefore);
    }

    @Test
    void anIdTokenThatDoesNotCheckOutSignsNobodyIn() throws Exception {
        for (Tamper tamper : new Tamper[] {
                Tamper.OTHER_AUDIENCE, Tamper.OTHER_NONCE, Tamper.OTHER_ISSUER, Tamper.FOREIGN_KEY, Tamper.EXPIRED}) {
            String email = uniqueEmail();
            MockHttpServletResponse start = start("/account", null);
            String answer = google.approve(start.getHeader("Location"), person(email), tamper);
            MockHttpServletResponse back = mvc.perform(get("/api/v1/auth/google/callback?" + answer)
                            .cookie(start.getCookie("SESSION")).with(uniqueClientIp()))
                    .andExpect(status().isFound()).andReturn().getResponse();
            assertThat(back.getHeader("Location")).as(tamper.name()).isEqualTo("/login?redirect=%2Faccount&google=failed");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?::citext", Integer.class, email))
                    .as(tamper.name()).isZero();
        }
    }

    @Test
    void onlyPathsOnThisSiteAreUsedAsTheWayBack() throws Exception {
        for (String returnTo : new String[] {"https://evil.example/", "//evil.example/", "/\\evil.example", "/api/v1/auth/me",
                "/shop with space"}) {
            Arrival arrival = signIn(person(uniqueEmail()), returnTo);
            assertThat(arrival.location()).as(returnTo).isEqualTo("/account");
        }
        assertThat(signIn(person(uniqueEmail()), "/shop?species=amano&page=2").location())
                .isEqualTo("/shop?species=amano&page=2");
    }

    @Test
    void customersWithoutPasswordConfirmWithGoogleBeforeDeletingTheirAccount() throws Exception {
        String email = uniqueEmail();
        Person grace = person(email);
        Cookie session = signIn(grace, "/account").session();

        mvc.perform(json(delete("/api/v1/account"), "{}").cookie(session))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CONFIRMATION_REQUIRED"));

        // Another Google account does not count.
        MockHttpServletResponse wrong = start("/account/security", session, "confirm");
        assertThat(callback(google.approve(wrong.getHeader("Location"), person(uniqueEmail())), session).location())
                .isEqualTo("/account/security?google=not_connected");

        MockHttpServletResponse confirm = start("/account/security", session, "confirm");
        Arrival confirmed = callback(google.approve(confirm.getHeader("Location"), grace), session);
        assertThat(confirmed.location()).isEqualTo("/account/security?google=confirmed");
        mvc.perform(get("/api/v1/account/profile").cookie(session))
                .andExpect(jsonPath("$.confirmedUntil").exists());

        mvc.perform(json(delete("/api/v1/account"), "{}").cookie(session)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_identities WHERE subject = ?", Integer.class,
                grace.subject())).isZero();
        // Signing in with the same Google account again starts a new, empty account.
        Cookie fresh = signIn(grace, "/account").session();
        mvc.perform(get("/api/v1/account/profile").cookie(fresh)).andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void aConfirmationIsUsedOnceForChangingTheAddress() throws Exception {
        Person grace = person(uniqueEmail());
        Cookie session = signIn(grace, "/account").session();
        MockHttpServletResponse confirm = start("/account/security", session, "confirm");
        callback(google.approve(confirm.getHeader("Location"), grace), session);

        mvc.perform(json(put("/api/v1/account/email"), "{\"newEmail\":\"%s\"}".formatted(uniqueEmail())).cookie(session))
                .andExpect(status().isAccepted());
        mvc.perform(json(put("/api/v1/account/email"), "{\"newEmail\":\"%s\"}".formatted(uniqueEmail())).cookie(session))
                .andExpect(jsonPath("$.code").value("CONFIRMATION_REQUIRED"));
    }

    @Test
    void confirmingNeedsASignedInCustomer() throws Exception {
        MockHttpServletResponse start = start("/account/security", null, "confirm");
        assertThat(start.getHeader("Location")).isEqualTo("/login?redirect=%2Faccount%2Fsecurity&google=failed");
    }

    @Test
    void googleCanBeDisconnectedOnlyWhileThereIsAPassword() throws Exception {
        Cookie googleOnly = signIn(person(uniqueEmail()), "/account").session();
        mvc.perform(json(delete("/api/v1/account/google"), "{}").cookie(googleOnly))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LAST_SIGN_IN_METHOD"));

        String email = uniqueEmail();
        auth.registeredSession(email);
        Cookie both = signIn(person(email), "/account").session();
        mvc.perform(json(delete("/api/v1/account/google"), "{}").cookie(both)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/account/profile").cookie(both)).andExpect(jsonPath("$.googleEmail").doesNotExist());
        assertThat(auth.emailsTo(email).getLast().getPayload().subject()).isEqualTo("Sign-in with Google removed");
        mvc.perform(json(delete("/api/v1/account/google"), "{}").cookie(both)).andExpect(status().isNotFound());
    }

    @Test
    void disconnectingNeedsASession() throws Exception {
        mvc.perform(json(delete("/api/v1/account/google"), "{}")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ helpers

    private MockHttpServletResponse start(String returnTo, Cookie session) throws Exception {
        return start(returnTo, session, "sign-in");
    }

    private MockHttpServletResponse start(String returnTo, Cookie session, String intent) throws Exception {
        var request = get("/api/v1/auth/google").param("returnTo", returnTo).param("intent", intent).with(uniqueClientIp());
        if (session != null) {
            request.cookie(session);
        }
        return mvc.perform(request).andExpect(status().isFound()).andReturn().getResponse();
    }

    private Arrival callback(String answer, Cookie session) throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/api/v1/auth/google/callback?" + answer)
                        .cookie(session).with(uniqueClientIp()))
                .andExpect(status().isFound())
                .andReturn().getResponse();
        Cookie renewed = response.getCookie("SESSION");
        return new Arrival(response.getHeader("Location"), renewed != null ? renewed : session);
    }

    private Arrival signIn(Person person, String returnTo) throws Exception {
        MockHttpServletResponse start = start(returnTo, null);
        return callback(google.approve(start.getHeader("Location"), person), start.getCookie("SESSION"));
    }

    private String signInRefused(Person person, String returnTo) throws Exception {
        Arrival arrival = signIn(person, returnTo);
        mvc.perform(get("/api/v1/auth/me").cookie(arrival.session())).andExpect(jsonPath("$.authenticated").value(false));
        return arrival.location();
    }
}
