package com.pwaquatics.shop.identity.application;

import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.identity.domain.EmailAddress;
import com.pwaquatics.shop.identity.domain.User;
import com.pwaquatics.shop.identity.domain.UserRole;
import com.pwaquatics.shop.identity.infrastructure.UserRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator from {@code APP_ADMIN_BOOTSTRAP_EMAIL/PASSWORD} when no
 * administrator exists yet. Remove the variables after the first start.
 */
@Component
class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final Clock clock;

    AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder, AppProperties properties, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.AdminBootstrap config = properties.adminBootstrap();
        if (config == null || !config.isConfigured() || users.existsByRole(UserRole.ADMIN)) {
            return;
        }
        String email = EmailAddress.normalise(config.email());
        if (config.password().length() < PasswordPolicy.MIN_LENGTH) {
            log.warn("Admin bootstrap skipped: the password must have at least {} characters", PasswordPolicy.MIN_LENGTH);
            return;
        }
        if (users.existsByEmail(email)) {
            log.warn("Admin bootstrap skipped: the configured address already belongs to a customer account");
            return;
        }
        users.save(User.administrator(email, passwordEncoder.encode(config.password()), clock.instant()));
        log.info("Created the first administrator account from the bootstrap configuration");
    }
}
