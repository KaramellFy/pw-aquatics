package com.pwaquatics.shop.identity.application;

import com.pwaquatics.shop.audit.application.AuditService;
import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.identity.domain.EmailAddress;
import com.pwaquatics.shop.identity.domain.User;
import com.pwaquatics.shop.identity.domain.UserToken;
import com.pwaquatics.shop.identity.domain.UserTokenType;
import com.pwaquatics.shop.identity.infrastructure.UserRepository;
import com.pwaquatics.shop.notification.application.OutboxService;
import com.pwaquatics.shop.security.ratelimit.RateLimiter;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account registration and email verification.
 *
 * <p>No account enumeration: registering and re-sending behave the same from the outside
 * whether or not the address already has an account. What differs is only the email sent.
 */
@Service
public class RegistrationService {

    static final String RATE_LIMIT = "auth-email";

    private final UserRepository users;
    private final TokenService tokens;
    private final AuthEmails emails;
    private final OutboxService outbox;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    private final AppProperties properties;
    private final Clock clock;

    RegistrationService(
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

    @Transactional
    public void register(RegisterAccount command, String clientIp) {
        rateLimiter.consume(RATE_LIMIT, clientIp);
        String email = EmailAddress.normalise(command.email());
        PasswordPolicy.check(command.password(), email);
        // Hash before looking up the address, so both paths take the same time.
        String passwordHash = passwordEncoder.encode(command.password());

        Optional<User> existing = users.findByEmail(email);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.isEmailVerified()) {
                outbox.enqueueEmail(emails.alreadyRegistered(user));
            } else if (user.isActive()) {
                // Unverified account: send a fresh link, but never replace the password someone else set.
                sendVerification(user);
            }
            return;
        }

        Instant now = clock.instant();
        User user = User.register(
                email, passwordHash, command.firstName(), command.lastName(), command.locale(), now);
        user.updateProfile(command.firstName(), command.lastName(), null, command.marketingOptIn(), now);
        users.save(user);
        sendVerification(user);
        audit.recordUserAction(user.getId(), "USER_REGISTERED", clientIp);
    }

    @Transactional
    public void resendVerification(String rawEmail, String clientIp) {
        rateLimiter.consume(RATE_LIMIT, clientIp);
        users.findByEmail(EmailAddress.normalise(rawEmail))
                .filter(user -> user.isActive() && !user.isEmailVerified())
                .ifPresent(this::sendVerification);
    }

    /**
     * Redeems a link from a verification or email-change email.
     *
     * @throws BusinessRuleException {@code TOKEN_INVALID}, or {@code EMAIL_TAKEN} if the new address
     *     was registered by someone else in the meantime
     */
    @Transactional
    public void confirmEmail(String rawToken, String clientIp) {
        UserToken token = tokens.redeem(
                rawToken, Set.of(UserTokenType.EMAIL_VERIFICATION, UserTokenType.EMAIL_CHANGE));
        User user = token.getUser();
        Instant now = clock.instant();
        if (token.getType() == UserTokenType.EMAIL_VERIFICATION) {
            user.verifyEmail(now);
            return;
        }
        if (users.existsByEmail(token.getNewEmail())) {
            throw new BusinessRuleException("EMAIL_TAKEN", "This email address is already in use.");
        }
        String oldEmail = user.getEmail();
        user.changeEmail(token.getNewEmail(), now);
        outbox.enqueueEmail(emails.emailChanged(oldEmail, user));
        audit.record(user.getId(), "USER_EMAIL_CHANGED", "USER", user.getId(), Map.of(), clientIp);
    }

    private void sendVerification(User user) {
        String token = tokens.issue(
                user, UserTokenType.EMAIL_VERIFICATION, properties.security().verificationTokenTtl(), null);
        outbox.enqueueEmail(emails.verification(user, token));
    }
}
