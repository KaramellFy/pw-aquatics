package com.pwaquatics.shop.identity.application;

import com.pwaquatics.shop.audit.application.AuditService;
import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.identity.domain.EmailAddress;
import com.pwaquatics.shop.identity.domain.User;
import com.pwaquatics.shop.identity.domain.UserToken;
import com.pwaquatics.shop.identity.domain.UserTokenType;
import com.pwaquatics.shop.identity.infrastructure.UserRepository;
import com.pwaquatics.shop.notification.application.OutboxService;
import com.pwaquatics.shop.security.ratelimit.RateLimiter;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** "Forgot password" flow via an emailed, single-use link. */
@Service
public class PasswordResetService {

    private final UserRepository users;
    private final TokenService tokens;
    private final AuthEmails emails;
    private final OutboxService outbox;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    private final AppProperties properties;
    private final Clock clock;

    PasswordResetService(
            UserRepository users,
            TokenService tokens,
            AuthEmails emails,
            OutboxService outbox,
            PasswordEncoder passwordEncoder,
            RateLimiter rateLimiter,
            AuditService audit,
            AppProperties properties,
            Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.emails = emails;
        this.outbox = outbox;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    /** Sends a reset link if the address belongs to a usable account; otherwise does nothing. */
    @Transactional
    public void requestReset(String rawEmail, String clientIp) {
        rateLimiter.consume(RegistrationService.RATE_LIMIT, clientIp);
        users.findByEmail(EmailAddress.normalise(rawEmail))
                .filter(User::isActive)
                .ifPresent(user -> {
                    String token = tokens.issue(
                            user, UserTokenType.PASSWORD_RESET, properties.security().passwordResetTokenTtl(), null);
                    outbox.enqueueEmail(emails.passwordReset(user, token));
                });
    }

    /**
     * Sets a new password with a valid reset link. Opening the link also proves ownership of
     * the address, so the email counts as verified afterwards.
     *
     * @return the user's id, so the caller can end all of the user's sessions
     */
    @Transactional
    public UUID resetPassword(String rawToken, String newPassword, String clientIp) {
        UserToken token = tokens.redeem(rawToken, Set.of(UserTokenType.PASSWORD_RESET));
        User user = token.getUser();
        PasswordPolicy.check(newPassword, user.getEmail());
        Instant now = clock.instant();
        user.changePassword(passwordEncoder.encode(newPassword), now);
        user.verifyEmail(now);
        outbox.enqueueEmail(emails.passwordChanged(user));
        audit.recordUserAction(user.getId(), "USER_PASSWORD_RESET", clientIp);
        return user.getId();
    }
}
