package com.pwaquatics.shop.security;

import com.pwaquatics.shop.common.config.AppProperties;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * HTTP security for the API.
 *
 * <ul>
 *   <li>Authentication: server-side sessions (Spring Session JDBC) with an HttpOnly cookie.
 *       Login itself is handled by {@code AuthController} + {@link SessionManager}.
 *   <li>CSRF: cookie-to-header pattern for the SPA ({@code XSRF-TOKEN} cookie → {@code X-XSRF-TOKEN} header).
 *       Payment provider webhooks are exempt: they carry no cookies and prove their origin with a
 *       signature instead.
 *   <li>Access: secure by default — every endpoint is denied unless explicitly opened below.
 *       Admin endpoints additionally use method security.
 *   <li>Errors: delegated to {@link com.pwaquatics.shop.common.api.ApiExceptionHandler} so 401/403
 *       responses use the same Problem Details format as everything else.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    static final String[] PUBLIC_GET = {
        "/api/v1/auth/csrf", "/api/v1/auth/me",
        "/api/v1/products", "/api/v1/products/**",
        "/api/v1/categories", "/api/v1/species", "/api/v1/species/**",
        "/api/v1/colours", "/api/v1/grades",
        "/api/v1/gallery", "/api/v1/shop-info",
        "/media/**",
        "/actuator/health", "/actuator/health/**",
    };

    /** Called by Stripe's servers; the signature is checked in {@code StripeWebhooks}. */
    static final String WEBHOOKS = "/api/v1/webhooks/stripe";

    /** Callable without a session. Each is rate limited in its service. */
    static final String[] PUBLIC_POST = {
        "/api/v1/contact",
        "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/logout",
        "/api/v1/auth/verify-email", "/api/v1/auth/resend-verification",
        "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password",
        WEBHOOKS,
    };

    /** Only reachable when springdoc is enabled (dev/staging); disabled in production. */
    static final String[] API_DOCS = {"/api/v3/api-docs", "/api/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};

    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            CsrfTokenRepository csrfTokenRepository,
            SecurityContextRepository securityContextRepository,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver)
            throws Exception {
        http.csrf(csrf -> csrf.spa()
                        .csrfTokenRepository(csrfTokenRepository)
                        .ignoringRequestMatchers(WEBHOOKS))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                        .requestMatchers(API_DOCS).permitAll()
                        // Cart: guests and customers alike (the service scopes it to the caller).
                        .requestMatchers("/api/v1/cart", "/api/v1/cart/**").permitAll()
                        .requestMatchers("/api/v1/account/**").authenticated()
                        .requestMatchers("/api/v1/checkout/**").authenticated()
                        .requestMatchers("/api/v1/payments/**").authenticated()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) ->
                                exceptionResolver.resolveException(request, response, null, ex))
                        .accessDeniedHandler((request, response, ex) ->
                                exceptionResolver.resolveException(request, response, null, ex)))
                .headers(headers -> headers
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .frameOptions(frame -> frame.deny()));
        return http.build();
    }

    /** The CSRF token lives in a cookie the SPA can read (it is not a secret from our own pages). */
    @Bean
    CsrfTokenRepository csrfTokenRepository(AppProperties properties) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .sameSite("Lax")
                .secure(properties.cookies().secure()));
        return repository;
    }

    /** The logged-in user is kept in the server-side session only. */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** Session cookie: not readable by scripts, same-site, HTTPS-only in production. */
    @Bean
    CookieSerializer sessionCookieSerializer(AppProperties properties) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(properties.cookies().secure());
        serializer.setSameSite("Lax");
        return serializer;
    }

    /**
     * Argon2id for new passwords. Hashes carry an id prefix ({@code {argon2}…}), so the
     * algorithm can be changed later and old hashes are upgraded on the next login.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = Map.of(
                "argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
                "bcrypt", new BCryptPasswordEncoder());
        return new DelegatingPasswordEncoder("argon2", encoders);
    }
}
