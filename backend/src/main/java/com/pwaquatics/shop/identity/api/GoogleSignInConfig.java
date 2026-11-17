package com.pwaquatics.shop.identity.api;

import com.pwaquatics.shop.common.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

/** Google as an OpenID Connect client, when {@code GOOGLE_CLIENT_ID} and {@code GOOGLE_CLIENT_SECRET} are set. */
@Configuration(proxyBeanMethods = false)
class GoogleSignInConfig {

    @Bean
    GoogleAuthorizationFlow googleAuthorizationFlow(AppProperties properties) {
        AppProperties.Google google = properties.google();
        ClientRegistration registration = google.isConfigured()
                ? CommonOAuth2Provider.GOOGLE.getBuilder(GoogleAuthorizationFlow.REGISTRATION_ID)
                        .clientId(google.clientId())
                        .clientSecret(google.clientSecret())
                        .redirectUri(stripTrailingSlash(properties.baseUrl()) + GoogleSignInController.CALLBACK_PATH)
                        .scope("openid", "email", "profile")
                        .build()
                : null;
        return new GoogleAuthorizationFlow(registration, google.timeout());
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
