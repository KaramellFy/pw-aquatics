package com.pwaquatics.shop.identity.application;

import com.pwaquatics.shop.audit.application.AuditService;
import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.identity.domain.User;
import com.pwaquatics.shop.identity.infrastructure.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the outcome of login attempts in their own short transactions, so a failed
 * attempt is recorded even though the login itself ends with an exception.
 */
@Component
class LoginRecorder {

    private final UserRepository users;
    private final AuditService audit;
    private final AppProperties properties;
    private final Clock clock;

    LoginRecorder(UserRepository users, AuditService audit, AppProperties properties, Clock clock) {
        this.users = users;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    void recordFailure(UUID userId, String clientIp) {
        User user = users.findById(userId).orElseThrow();
        Instant now = clock.instant();
        user.recordFailedLogin(now, properties.security().maxFailedLogins(), properties.security().lockDuration());
        if (user.isTemporarilyLocked(now)) {
            audit.recordUserAction(userId, "USER_LOCKED_AFTER_FAILED_LOGINS", clientIp);
        }
    }

    /** @param upgradedPasswordHash a re-hash with the current algorithm, or null to keep the stored hash */
    @Transactional
    User recordSuccess(UUID userId, String upgradedPasswordHash) {
        User user = users.findById(userId).orElseThrow();
        Instant now = clock.instant();
        if (upgradedPasswordHash != null) {
            user.changePassword(upgradedPasswordHash, now);
        }
        user.recordSuccessfulLogin(now);
        return user;
    }
}
