package com.pwaquatics.shop.support;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.identity.api.GoogleAuthorizationFlow;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * A stand-in for Google's sign-in servers on a local port (the tests never reach Google). It
 * behaves like Google where the shop depends on it: the token endpoint checks the client
 * credentials, the redirect URI and PKCE, and returns an RS256-signed ID token whose keys are
 * published at a JWKS endpoint. The test plays the customer's part at Google with {@link #approve}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeGoogle implements DisposableBean {

    public static final String CLIENT_ID = "test-google-client-id";
    private static final String CLIENT_SECRET = "test-google-client-secret";

    /** Ways a forged or broken answer can differ from a genuine one. */
    public enum Tamper {
        NONE, OTHER_AUDIENCE, OTHER_NONCE, OTHER_ISSUER, FOREIGN_KEY, EXPIRED
    }

    /** A Google account. */
    public record Person(String subject, String email, boolean emailVerified, String givenName, String familyName) {

        public Person withEmail(String newEmail) {
            return new Person(subject, newEmail, emailVerified, givenName, familyName);
        }
    }

    private record Grant(Person person, String nonce, String codeChallenge, String redirectUri, Tamper tamper) {}

    private final RSAKey key;
    private final RSAKey foreignKey;
    private final HttpServer server;
    private final String base;
    private final Map<String, Grant> grants = new ConcurrentHashMap<>();
    private final AtomicInteger tokenRequests = new AtomicInteger();

    public FakeGoogle() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("fake-google-key").generate();
        foreignKey = new RSAKeyGenerator(2048).keyID("fake-google-key").generate();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", this::token);
        server.createContext("/certs", exchange -> send(exchange, 200,
                new JWKSet(key.toPublicJWK()).toString()));
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Google as the shop's OAuth client registration, pointing at this stand-in. */
    @Bean
    @Primary
    GoogleAuthorizationFlow fakeGoogleAuthorizationFlow(AppProperties properties) {
        if (!properties.google().isConfigured()) {
            return new GoogleAuthorizationFlow(null, properties.google().timeout());
        }
        ClientRegistration registration = ClientRegistration.withRegistrationId("google")
                .clientId(properties.google().clientId())
                .clientSecret(properties.google().clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(properties.baseUrl() + "/api/v1/auth/google/callback")
                .scope("openid", "email", "profile")
                .authorizationUri(base + "/o/oauth2/v2/auth")
                .tokenUri(base + "/token")
                .jwkSetUri(base + "/certs")
                .issuerUri(base)
                .userNameAttributeName("sub")
                .clientName("Google")
                .build();
        return new GoogleAuthorizationFlow(registration, properties.google().timeout());
    }

    public static Person person(String email) {
        return new Person(UUID.randomUUID().toString().replace("-", ""), email, true, "Grace", "Hopper");
    }

    /** The address Google's sign-in page is opened at. */
    public String authorizationEndpoint() {
        return base + "/o/oauth2/v2/auth";
    }

    /**
     * The customer signs in at Google and agrees: Google sends the browser back with a code.
     *
     * @param authorizationUrl where the shop sent the browser (its {@code Location} header)
     * @return the query of the redirect back to the shop's callback ({@code code=…&state=…})
     */
    public String approve(String authorizationUrl, Person person) {
        return approve(authorizationUrl, person, Tamper.NONE);
    }

    public String approve(String authorizationUrl, Person person, Tamper tamper) {
        Map<String, String> query = query(URI.create(authorizationUrl).getRawQuery());
        String code = "code-" + UUID.randomUUID();
        grants.put(code, new Grant(
                person, query.get("nonce"), query.get("code_challenge"), query.get("redirect_uri"), tamper));
        return "code=" + code + "&state=" + URLDecoder.decode(query.get("state"), StandardCharsets.UTF_8);
    }

    /** How often the shop exchanged a code. */
    public int tokenRequests() {
        return tokenRequests.get();
    }

    /** The query parameters of a URL, decoded. */
    public static Map<String, String> query(String rawQuery) {
        Map<String, String> parameters = new HashMap<>();
        for (String pair : rawQuery.split("&")) {
            int equals = pair.indexOf('=');
            parameters.put(URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
        }
        return parameters;
    }

    private void token(HttpExchange exchange) throws IOException {
        tokenRequests.incrementAndGet();
        Map<String, String> form = query(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        String expectedAuth = "Basic " + Base64.getEncoder()
                .encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
        if (!expectedAuth.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            send(exchange, 401, "{\"error\":\"invalid_client\"}");
            return;
        }
        Grant grant = grants.remove(form.getOrDefault("code", ""));
        if (!"authorization_code".equals(form.get("grant_type")) || grant == null
                || !grant.redirectUri().equals(form.get("redirect_uri"))
                || !grant.codeChallenge().equals(s256(form.getOrDefault("code_verifier", "")))) {
            send(exchange, 400, "{\"error\":\"invalid_grant\"}");
            return;
        }
        try {
            send(exchange, 200, """
                    {"access_token":"fake-access-token","token_type":"Bearer","expires_in":3599,
                     "scope":"openid https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile",
                     "id_token":"%s"}
                    """.formatted(idToken(grant)));
        } catch (Exception e) {
            send(exchange, 500, "{\"error\":\"server_error\"}");
        }
    }

    private String idToken(Grant grant) throws Exception {
        Instant now = Instant.now();
        Person person = grant.person();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(grant.tamper() == Tamper.OTHER_ISSUER ? "https://accounts.example.com" : base)
                .subject(person.subject())
                .audience(List.of(grant.tamper() == Tamper.OTHER_AUDIENCE ? "someone-elses-client" : CLIENT_ID))
                .issueTime(Date.from(now.minusSeconds(grant.tamper() == Tamper.EXPIRED ? 7200 : 0)))
                .expirationTime(Date.from(grant.tamper() == Tamper.EXPIRED ? now.minusSeconds(3600) : now.plusSeconds(3600)))
                .claim("nonce", grant.tamper() == Tamper.OTHER_NONCE ? "another-nonce" : grant.nonce())
                .claim("azp", CLIENT_ID)
                .claim("email", person.email())
                .claim("email_verified", person.emailVerified())
                .claim("given_name", person.givenName())
                .claim("family_name", person.familyName())
                .claim("name", person.givenName() + " " + person.familyName())
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(),
                claims);
        jwt.sign(new RSASSASigner(grant.tamper() == Tamper.FOREIGN_KEY ? foreignKey : key));
        return jwt.serialize();
    }

    private static String s256(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void destroy() {
        server.stop(0);
    }
}
