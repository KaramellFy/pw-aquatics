package com.pwaquatics.shop.identity.application;

import com.pwaquatics.shop.audit.application.AuditService;
import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.common.error.NotFoundException;
import com.pwaquatics.shop.identity.application.view.ProfileView;
import com.pwaquatics.shop.identity.domain.EmailAddress;
import com.pwaquatics.shop.identity.domain.User;
import com.pwaquatics.shop.identity.domain.UserAnonymised;
import com.pwaquatics.shop.identity.domain.UserTokenType;
import com.pwaquatics.shop.identity.infrastructure.UserRepository;
import com.pwaquatics.shop.notification.application.OutboxService;
import com.pwaquatics.shop.security.ratelimit.RateLimiter;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What a logged-in user can do with their own account. Sensitive changes need the current password. */
@Service
public class AccountService {

    static final String MARKETING_CONSENT_GIVEN = "MARKETING_CONSENT_GIVEN";
    static final String MARKETING_CONSENT_WITHDRAWN = "MARKETING_CONSENT_WITHDRAWN";

    private final UserRepository users;
    private final TokenService tokens;
    private final AuthEmails emails;
    private final OutboxService outbox;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final AppProperties properties;
    private final Clock clock;

    AccountService(
            UserRepository users,
            TokenService tokens,
            AuthEmails emails,
            OutboxService outbox,
            PasswordEncoder passwordEncoder,
            RateLimiter rateLimiter,
            AuditService audit,
            ApplicationEventPublisher events,
            AppProperties properties,
            Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.emails = emails;
        this.outbox = outbox;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProfileView profile(UUID userId) {
        return toView(load(userId));
    }

    /**
     * Updates the profile. Giving or withdrawing newsletter consent is recorded in the audit
     * log, so the shop can show when consent was given (GDPR Art. 7(1)).
     */
    @Transactional
    public ProfileView updateProfile(UUID userId, UpdateProfile command, String clientIp) {
        User user = load(userId);
        Instant now = clock.instant();
        boolean consentBefore = user.isMarketingOptIn();
        if (command.marketingOptIn() != consentBefore) {
            audit.record(userId, command.marketingOptIn() ? MARKETING_CONSENT_GIVEN : MARKETING_CONSENT_WITHDRAWN,
                    "USER", userId, Map.of("source", "profile"), clientIp);
        }
        user.updateProfile(command.firstName(), command.lastName(), command.phone(), command.marketingOptIn(), now);
        if (command.locale() != null) {
            user.setLocale(command.locale(), now);
        }
        return toView(user);
    }

    /** Changes the password. The caller ends the user's other sessions afterwards. */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword, String clientIp) {
        User user = load(userId);
        requirePassword(user, currentPassword);
        PasswordPolicy.check(newPassword, user.getEmail());
        user.changePassword(passwordEncoder.encode(newPassword), clock.instant());
        outbox.enqueueEmail(emails.passwordChanged(user));
        audit.recordUserAction(userId, "USER_PASSWORD_CHANGED", clientIp);
    }

    /**
     * Starts an email change: a confirmation link goes to the new address and the change
     * only happens when it is opened. If the address is already taken nothing is sent,
     * and the caller cannot tell the difference.
     */
    @Transactional
    public void requestEmailChange(UUID userId, String rawNewEmail, String currentPassword) {
        User user = load(userId);
        requirePassword(user, currentPassword);
        String newEmail = EmailAddress.normalise(rawNewEmail);
        if (newEmail.equals(user.getEmail())) {
            throw new BusinessRuleException("EMAIL_UNCHANGED", "This is already your email address.");
        }
        if (users.existsByEmail(newEmail)) {
            return;
        }
        String token = tokens.issue(
                user, UserTokenType.EMAIL_CHANGE, properties.security().verificationTokenTtl(), newEmail);
        outbox.enqueueEmail(emails.emailChangeConfirmation(user, newEmail, token));
    }

    /**
     * Deletes the account: personal data is removed and the row anonymised (orders must be
     * kept for the statutory period). Other modules clean up via {@link UserAnonymised}.
     * The caller ends all sessions afterwards.
     */
    @Transactional
    public void deleteAccount(UUID userId, String currentPassword, String clientIp) {
        User user = load(userId);
        requirePassword(user, currentPassword);
        String email = user.getEmail();
        String firstName = user.getFirstName();
        boolean german = "de".equals(user.getLocale());

        tokens.deleteAllFor(user);
        user.anonymise(clock.instant());
        events.publishEvent(new UserAnonymised(userId));
        outbox.enqueueEmail(emails.accountDeleted(email, firstName, german));
        audit.recordUserAction(userId, "USER_ACCOUNT_DELETED", clientIp);
    }

    private void requirePassword(User user, String password) {
        // Limits guessing through a stolen session.
        rateLimiter.consume("password-check", user.getId().toString());
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessRuleException("CURRENT_PASSWORD_INVALID", "The current password is not correct.");
        }
    }

    private User load(UUID userId) {
        return users.findById(userId).filter(User::isActive)
                .orElseThrow(() -> new NotFoundException("Account not found."));
    }

    private static ProfileView toView(User user) {
        return new ProfileView(
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.isMarketingOptIn(),
                user.getLocale(),
                user.getCreatedAt());
    }
}
