package com.pwaquatics.shop.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.identity.domain.User;
import com.pwaquatics.shop.identity.domain.UserRole;
import com.pwaquatics.shop.identity.infrastructure.UserRepository;
import com.pwaquatics.shop.support.IntegrationTest;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

@IntegrationTest
class AdminBootstrapTest {

    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AppProperties properties;
    @Autowired Clock clock;

    @Test
    void createsTheFirstAdministratorOnlyOnce() {
        users.findAll().stream().filter(user -> user.getRole() == UserRole.ADMIN).forEach(users::delete);
        String email = "Admin-" + UUID.randomUUID() + "@Example.com";

        bootstrap(email, "an-initial-admin-password").run(null);

        User admin = users.findByEmail(email.toLowerCase()).orElseThrow();
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.isEmailVerified()).isTrue();
        assertThat(passwordEncoder.matches("an-initial-admin-password", admin.getPasswordHash())).isTrue();

        // A second start with different settings changes nothing while an admin exists.
        bootstrap("second-" + email, "another-admin-password").run(null);
        assertThat(users.findByEmail(("second-" + email).toLowerCase())).isEmpty();
    }

    @Test
    void doesNothingWithoutConfigurationOrWithATooShortPassword() {
        users.findAll().stream().filter(user -> user.getRole() == UserRole.ADMIN).forEach(users::delete);
        long before = users.count();

        bootstrap("", "").run(null);
        bootstrap("short-" + UUID.randomUUID() + "@example.com", "short").run(null);

        assertThat(users.count()).isEqualTo(before);
    }

    private AdminBootstrap bootstrap(String email, String password) {
        AppProperties configured = new AppProperties(
                properties.baseUrl(),
                properties.cookies(),
                properties.mail(),
                properties.outbox(),
                properties.media(),
                properties.security(),
                new AppProperties.AdminBootstrap(email, password),
                properties.rateLimits());
        return new AdminBootstrap(users, passwordEncoder, configured, clock);
    }
}
