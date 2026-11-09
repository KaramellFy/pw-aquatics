package com.pwaquatics.shop.security;

import com.pwaquatics.shop.common.config.AppProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * HTTP security for the API.
 *
 * <ul>
 *   <li>CSRF: cookie-to-header pattern for the SPA ({@code XSRF-TOKEN} cookie → {@code X-XSRF-TOKEN} header).
 *   <li>Access: secure by default — every endpoint is denied unless explicitly opened below.
 *       Customer and admin rules arrive with authentication (Phase 3).
 *   <li>Errors: delegated to {@link com.pwaquatics.shop.common.api.ApiExceptionHandler} so 401/403
 *       responses use the same Problem Details format as everything else.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    static final String[] PUBLIC_GET = {
        "/api/v1/auth/csrf",
        "/api/v1/products", "/api/v1/products/**",
        "/api/v1/categories", "/api/v1/species", "/api/v1/species/**",
        "/api/v1/colours", "/api/v1/grades",
        "/media/**",
        "/actuator/health", "/actuator/health/**",
    };

    /** Only reachable when springdoc is enabled (dev/staging); disabled in production. */
    static final String[] API_DOCS = {"/api/v3/api-docs", "/api/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};

    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            AppProperties properties,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver)
            throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .sameSite("Lax")
                .secure(properties.cookies().secure()));

        http.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfRepository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/contact").permitAll()
                        .requestMatchers(API_DOCS).permitAll()
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
}
