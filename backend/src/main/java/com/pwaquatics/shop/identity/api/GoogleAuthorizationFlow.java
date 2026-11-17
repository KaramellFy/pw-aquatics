package com.pwaquatics.shop.identity.api;

import com.pwaquatics.shop.identity.application.GoogleAccount;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.oidc.authentication.OidcAuthorizationCodeAuthenticationProvider;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * The OpenID Connect "authorization code" flow with Google, built from Spring Security's OAuth 2
 * client parts (docs/adr/014-sign-in-with-google.md).
 *
 * <p>{@link #start} creates the authorization request — with a random {@code state} tied to this
 * browser session, a {@code nonce} and PKCE — keeps it in the session and returns Google's address.
 * {@link #finish} only accepts Google's answer for that same request: it exchanges the code
 * server-to-server and checks the ID token's signature against Google's published keys, its
 * issuer, audience, expiry and nonce. Google's access token is not kept; Google's user-info
 * endpoint is not called (the ID token has everything needed).
 */
public class GoogleAuthorizationFlow {

    static final String REGISTRATION_ID = "google";
    private static final Logger log = LoggerFactory.getLogger(GoogleAuthorizationFlow.class);
    private static final String INTENT = "pwa.intent";
    private static final String RETURN_TO = "pwa.returnTo";
    private static final String LANGUAGE = "pwa.language";

    /** Why the customer started the flow. */
    enum Intent {
        /** From the sign-in or registration page. */
        SIGN_IN,
        /** Signed in already: confirming with Google instead of typing a password. */
        CONFIRM
    }

    /** Where the flow started and where the customer goes back to. */
    record Origin(Intent intent, String returnTo, String language) {}

    /** How the round trip through Google ended. */
    sealed interface Outcome {}

    /** Google confirmed the account. */
    record Confirmed(Origin origin, GoogleAccount account) implements Outcome {}

    /** The customer cancelled at Google. */
    record Cancelled(Origin origin) implements Outcome {}

    /** Something went wrong after the flow started (Google's answer could not be verified). */
    record Failed(Origin origin) implements Outcome {}

    /** No flow was started in this browser session, or it was already finished. */
    record Unknown() implements Outcome {}

    private final @Nullable ClientRegistration registration;
    private final @Nullable InMemoryClientRegistrationRepository registrations;
    private final @Nullable OidcAuthorizationCodeAuthenticationProvider provider;
    private final AuthorizationRequestRepository<OAuth2AuthorizationRequest> requests =
            new HttpSessionOAuth2AuthorizationRequestRepository();

    /**
     * @param registration Google as an OAuth client registration, or null when Google sign-in is
     *     not configured
     * @param timeout for each call to Google
     */
    public GoogleAuthorizationFlow(@Nullable ClientRegistration registration, Duration timeout) {
        this.registration = registration;
        if (registration == null) {
            this.registrations = null;
            this.provider = null;
            return;
        }
        this.registrations = new InMemoryClientRegistrationRepository(registration);
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .proxy(ProxySelector.getDefault())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(http);
        requestFactory.setReadTimeout(timeout);

        RestClientAuthorizationCodeTokenResponseClient tokens = new RestClientAuthorizationCodeTokenResponseClient();
        tokens.setRestClient(RestClient.builder()
                .requestFactory(requestFactory)
                .configureMessageConverters(converters -> converters
                        .registerDefaults()
                        .addCustomConverter(new FormHttpMessageConverter())
                        .addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter()))
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .build());

        // Google's signing keys, fetched with the same timeouts and cached by the decoder.
        NimbusJwtDecoder idTokens = NimbusJwtDecoder.withJwkSetUri(registration.getProviderDetails().getJwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(new RestTemplate(requestFactory))
                .build();
        idTokens.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(), new OidcIdTokenValidator(registration)));
        idTokens.setClaimSetConverter(OidcIdTokenDecoderFactory.createDefaultClaimTypeConverter());

        OidcAuthorizationCodeAuthenticationProvider oidc = new OidcAuthorizationCodeAuthenticationProvider(
                tokens, request -> new DefaultOidcUser(
                        List.of(new SimpleGrantedAuthority("OIDC_USER")), request.getIdToken()));
        oidc.setJwtDecoderFactory(ignored -> idTokens);
        this.provider = oidc;
    }

    /** Whether Google sign-in is configured. */
    public boolean isAvailable() {
        return registration != null;
    }

    /**
     * Starts the flow for this browser session (a later start replaces an unfinished one).
     *
     * @return Google's address to send the browser to
     */
    URI start(HttpServletRequest request, HttpServletResponse response, Origin origin) {
        if (registrations == null) {
            throw new IllegalStateException("Google sign-in is not configured");
        }
        DefaultOAuth2AuthorizationRequestResolver resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations);
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce()
                .andThen(builder -> builder
                        // Google's pages in the shop's language.
                        .additionalParameters(parameters -> parameters.put("hl", origin.language()))
                        .attributes(attributes -> attributes.putAll(Map.of(
                                INTENT, origin.intent().name(),
                                RETURN_TO, origin.returnTo(),
                                LANGUAGE, origin.language())))));
        OAuth2AuthorizationRequest authorization = resolver.resolve(request, REGISTRATION_ID);
        requests.saveAuthorizationRequest(authorization, request, response);
        return URI.create(authorization.getAuthorizationRequestUri());
    }

    /** Handles Google's redirect back to the shop. The started flow is used up either way. */
    Outcome finish(HttpServletRequest request, HttpServletResponse response) {
        if (provider == null) {
            return new Unknown();
        }
        // Only returned when the state parameter matches the request kept in this session.
        OAuth2AuthorizationRequest authorization = requests.removeAuthorizationRequest(request, response);
        if (authorization == null) {
            return new Unknown();
        }
        Origin origin = new Origin(
                Intent.valueOf(authorization.getAttribute(INTENT)),
                authorization.getAttribute(RETURN_TO),
                authorization.getAttribute(LANGUAGE));
        String error = request.getParameter("error");
        if (error != null) {
            return "access_denied".equals(error)
                    ? new Cancelled(origin)
                    : failed(origin, "error=" + (error.matches("[a-z_]{1,40}") ? error : "(other)"));
        }
        String code = request.getParameter("code");
        if (code == null || code.isBlank()) {
            return failed(origin, "no code");
        }
        OAuth2AuthorizationResponse answer = OAuth2AuthorizationResponse.success(code)
                .redirectUri(authorization.getRedirectUri())
                .state(request.getParameter("state"))
                .build();
        try {
            OAuth2LoginAuthenticationToken result = (OAuth2LoginAuthenticationToken) provider.authenticate(
                    new OAuth2LoginAuthenticationToken(
                            registration, new OAuth2AuthorizationExchange(authorization, answer)));
            OidcIdToken idToken = ((OidcUser) result.getPrincipal()).getIdToken();
            if (idToken.getEmail() == null) {
                return failed(origin, "no email in the ID token");
            }
            return new Confirmed(origin, new GoogleAccount(
                    idToken.getSubject(),
                    idToken.getEmail(),
                    Boolean.TRUE.equals(idToken.getEmailVerified()),
                    idToken.getGivenName(),
                    idToken.getFamilyName(),
                    idToken.getFullName()));
        } catch (OAuth2AuthenticationException e) {
            // The error code only (e.g. invalid_id_token, invalid_nonce); never tokens or personal data.
            return failed(origin, e.getError().getErrorCode());
        } catch (RuntimeException e) {
            log.warn("Google sign-in failed: {}", e.getClass().getSimpleName());
            return new Failed(origin);
        }
    }

    private static Failed failed(Origin origin, String reason) {
        log.warn("Google sign-in failed: {}", reason);
        return new Failed(origin);
    }
}
