package com.pwaquatics.shop.identity.application;

import com.pwaquatics.shop.audit.application.AuditService;
import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.common.error.NotFoundException;
import com.pwaquatics.shop.identity.application.GoogleSignInRefused.Reason;
import com.pwaquatics.shop.identity.domain.EmailAddress;
import com.pwaquatics.shop.identity.domain.IdentityProvider;
import com.pwaquatics.shop.identity.domain.User;
import com.pwaquatics.shop.identity.domain.UserIdentity;
import com.pwaquatics.shop.identity.domain.UserRole;
import com.pwaquatics.shop.identity.domain.UserStatus;
import com.pwaquatics.shop.identity.infrastructure.UserIdentityRepository;
import com.pwaquatics.shop.identity.infrastructure.UserRepository;
import com.pwaquatics.shop.notification.application.OutboxService;
import com.pwaquatics.shop.security.ShopUserPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What happens after Google has confirmed who is signing in (docs/adr/014-sign-in-with-google.md).
 *
 * <p>The Google account is found by its subject id. The first time, it is connected to the shop
 * account with the same address — but only if Google has verified that address — or a new account
 * is created. Administrators sign in with their password only.
 */
@Service
public class GoogleSignInService {

    private static final int NAME_LENGTH = 100;

    private final UserRepository users;
    private final UserIdentityRepository identities;
    private final AuthEmails emails;
    private final OutboxService outbox;
    private final AuditService audit;
    private final Clock clock;

    GoogleSignInService(
            UserRepository users,
            UserIdentityRepository identities,
            AuthEmails emails,
            OutboxService outbox,
            AuditService audit,
            Clock clock) {
        this.users = users;
        this.identities = identities;
        this.emails = emails;
        this.outbox = outbox;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Signs in with a Google account Google has just confirmed. Starting the session is done by
     * the web layer ({@code SessionManager}).
     *
     * @param language the language of the page the customer started from, for a new account
     * @throws GoogleSignInRefused when this Google account may not sign in here
     */
    @Transactional
    public ShopUserPrincipal signIn(GoogleAccount account, String language, String clientIp) {
        Instant now = clock.instant();
        Optional<UserIdentity> known = identities.findByProviderAndSubject(IdentityProvider.GOOGLE, account.subject());
        User user;
        if (known.isPresent()) {
            user = known.get().getUser();
            requireUsable(user);
            known.get().used(account.email(), now);
        } else {
            user = connectOrRegister(account, language, now, clientIp);
        }
        user.recordSuccessfulLogin(now);
        return new ShopUserPrincipal(user.getId(), user.getRole().name());
    }

    /**
     * The signed-in customer confirmed who they are with the Google account connected to their
     * shop account (instead of typing a password, e.g. before deleting the account).
     *
     * @throws GoogleSignInRefused {@code NOT_CONNECTED} for any other Google account
     */
    @Transactional(readOnly = true)
    public void confirm(UUID userId, GoogleAccount account) {
        User user = users.findById(userId).filter(User::isActive)
                .orElseThrow(() -> new GoogleSignInRefused(Reason.NOT_CONNECTED));
        identities.findByUserAndProvider(user, IdentityProvider.GOOGLE)
                .filter(identity -> identity.getSubject().equals(account.subject()))
                .orElseThrow(() -> new GoogleSignInRefused(Reason.NOT_CONNECTED));
    }

    /**
     * Removes the connection to Google. Only possible while the account has a password, so the
     * customer can still sign in afterwards.
     */
    @Transactional
    public void disconnect(UUID userId, String clientIp) {
        User user = users.findById(userId).filter(User::isActive)
                .orElseThrow(() -> new NotFoundException("Account not found."));
        UserIdentity identity = identities.findByUserAndProvider(user, IdentityProvider.GOOGLE)
                .orElseThrow(() -> new NotFoundException("No Google account is connected."));
        if (!user.hasPassword()) {
            throw new BusinessRuleException("LAST_SIGN_IN_METHOD",
                    "Set a password first, otherwise you could no longer sign in.");
        }
        identities.delete(identity);
        outbox.enqueueEmail(emails.googleDisconnected(user));
        audit.recordUserAction(userId, "USER_GOOGLE_DISCONNECTED", clientIp);
    }

    private User connectOrRegister(GoogleAccount account, String language, Instant now, String clientIp) {
        if (!account.emailVerified()) {
            throw new GoogleSignInRefused(Reason.UNVERIFIED_EMAIL);
        }
        String email = EmailAddress.normalise(account.email());
        Optional<User> existing = users.findByEmail(email);
        if (existing.isEmpty()) {
            User user = users.save(User.registerWithGoogle(
                    email, firstName(account), clip(account.familyName()), language, now));
            identities.save(new UserIdentity(user, IdentityProvider.GOOGLE, account.subject(), account.email(), now));
            audit.record(user.getId(), "USER_REGISTERED", "USER", user.getId(), Map.of("source", "google"), clientIp);
            return user;
        }
        User user = existing.get();
        if (user.getRole() == UserRole.ADMIN) {
            throw new GoogleSignInRefused(Reason.PASSWORD_ONLY);
        }
        requireUsable(user);
        if (identities.findByUserAndProvider(user, IdentityProvider.GOOGLE).isPresent()) {
            throw new GoogleSignInRefused(Reason.OTHER_GOOGLE_ACCOUNT);
        }
        user.confirmEmailThroughProvider(now);
        identities.save(new UserIdentity(user, IdentityProvider.GOOGLE, account.subject(), account.email(), now));
        outbox.enqueueEmail(emails.googleConnected(user, account.email()));
        audit.recordUserAction(user.getId(), "USER_GOOGLE_CONNECTED", clientIp);
        return user;
    }

    private static void requireUsable(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new GoogleSignInRefused(Reason.LOCKED);
        }
        if (user.getRole() == UserRole.ADMIN) {
            throw new GoogleSignInRefused(Reason.PASSWORD_ONLY);
        }
    }

    /** Google's given name, else its full name; the customer can correct it in the profile. */
    private static String firstName(GoogleAccount account) {
        String given = clip(account.givenName());
        return given.isEmpty() ? clip(account.name()) : given;
    }

    private static String clip(String value) {
        if (value == null) {
            return "";
        }
        String stripped = value.strip();
        return stripped.length() <= NAME_LENGTH ? stripped : stripped.substring(0, NAME_LENGTH);
    }
}
