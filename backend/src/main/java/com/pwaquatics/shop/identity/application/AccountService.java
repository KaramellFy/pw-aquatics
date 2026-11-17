package com.pwaquatics.shop.identity.application;

import com.pwaquatics.shop.audit.application.AuditService;
import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.common.error.NotFoundException;
import com.pwaquatics.shop.identity.application.view.ProfileView;
import com.pwaquatics.shop.identity.domain.EmailAddress;
import com.pwaquatics.shop.identity.domain.IdentityProvider;
import com.pwaquatics.shop.identity.domain.User;
import com.pwaquatics.shop.identity.domain.UserAnonymised;
import com.pwaquatics.shop.identity.domain.UserIdentity;
import com.pwaquatics.shop.identity.domain.UserTokenType;
import com.pwaquatics.shop.identity.infrastructure.UserIdentityRepository;
import com.pwaquatics.shop.identity.infrastructure.UserRepository;
import com.pwaquatics.shop.notification.application.OutboxService;
import com.pwaquatics.shop.security.ratelimit.RateLimiter;
import jakarta.annotation.Nullable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a logged-in user can do with their own account. Sensitive changes need the current
 * password, or — for changing the email address and deleting the account — a confirmation with
 * the connected Google account in the last {@link #CONFIRMATION_WINDOW} (customers who sign in
 * with Google may have no password).
 */
@Service
public class AccountService {

    static final String MARKETING_CONSENT_GIVEN = "MARKETING_CONSENT_GIVEN";
    static final String MARKETING_CONSENT_WITHDRAWN = "MARKETING_CONSENT_WITHDRAWN";

    /** How long a confirmation with Google stands in for the password. */
    public static final Duration CONFIRMATION_WINDOW = Duration.ofMinutes(10);

    private final UserRepository users;
    private final UserIdentityRepository identities;
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
            UserIdentityRepository identities,
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
        this.identities = identities;
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
        return profile(userId, null);
    }

    /** @param confirmedAt when this session last confirmed with Google, or null */
    @Transactional(readOnly = true)
    public ProfileView profile(UUID userId, @Nullable Instant confirmedAt) {
        return toView(load(userId), confirmedAt);
    }

    /**
     * Updates the profile. Giving or withdrawing newsletter consent is recorded in the audit
     * log, so the shop can show when consent was given (GDPR Art. 7(1)).
     */
    @Transactional
    public ProfileView updateProfile(
            UUID userId, UpdateProfile command, @Nullable Instant confirmedAt, String clientIp) {
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
        return toView(user, confirmedAt);
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
     *
     * @param currentPassword the password, or null after a confirmation with Google
     * @param confirmedAt when this session last confirmed with Google, or null
     */
    @Transactional
    public void requestEmailChange(
            UUID userId, String rawNewEmail, @Nullable String currentPassword, @Nullable Instant confirmedAt) {
        User user = load(userId);
        requireConfirmation(user, currentPassword, confirmedAt);
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
     *
     * @param currentPassword the password, or null after a confirmation with Google
     * @param confirmedAt when this session last confirmed with Google, or null
     */
    @Transactional
    public void deleteAccount(
            UUID userId, @Nullable String currentPassword, @Nullable Instant confirmedAt, String clientIp) {
        User user = load(userId);
        requireConfirmation(user, currentPassword, confirmedAt);
        String email = user.getEmail();
        String firstName = user.getFirstName();
        boolean german = "de".equals(user.getLocale());

        tokens.deleteAllFor(user);
        identities.deleteAllByUser(user);
        user.anonymise(clock.instant());
        events.publishEvent(new UserAnonymised(userId));
        outbox.enqueueEmail(emails.accountDeleted(email, firstName, german));
        audit.recordUserAction(userId, "USER_ACCOUNT_DELETED", clientIp);
    }

    /**
     * The current password, or a recent confirmation with the Google account connected to this
     * shop account.
     */
    private void requireConfirmation(User user, @Nullable String password, @Nullable Instant confirmedAt) {
        if (password != null && !password.isBlank()) {
            requirePassword(user, password);
            return;
        }
        if (isConfirmed(user, confirmedAt)) {
            return;
        }
        if (user.hasPassword()) {
            throw new BusinessRuleException("CURRENT_PASSWORD_INVALID", "The current password is not correct.");
        }
        throw new BusinessRuleException("CONFIRMATION_REQUIRED", "Please confirm with Google first.");
    }

    private boolean isConfirmed(User user, @Nullable Instant confirmedAt) {
        return confirmedUntil(confirmedAt) != null
                && identities.findByUserAndProvider(user, IdentityProvider.GOOGLE).isPresent();
    }

    private @Nullable Instant confirmedUntil(@Nullable Instant confirmedAt) {
        if (confirmedAt == null) {
            return null;
        }
        Instant until = confirmedAt.plus(CONFIRMATION_WINDOW);
        return until.isAfter(clock.instant()) ? until : null;
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

    private ProfileView toView(User user, @Nullable Instant confirmedAt) {
        String googleEmail = identities.findByUserAndProvider(user, IdentityProvider.GOOGLE)
                .map(UserIdentity::getEmail)
                .orElse(null);
        return new ProfileView(
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.isMarketingOptIn(),
                user.getLocale(),
                user.getCreatedAt(),
                user.hasPassword(),
                googleEmail,
                googleEmail == null ? null : confirmedUntil(confirmedAt));
    }
}
