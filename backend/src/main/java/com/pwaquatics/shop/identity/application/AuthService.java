package com.pwaquatics.shop.identity.application;

import com.pwaquatics.shop.common.error.ForbiddenException;
import com.pwaquatics.shop.common.error.LockedException;
import com.pwaquatics.shop.common.error.RateLimitedException;
import com.pwaquatics.shop.common.error.UnauthorizedException;
import com.pwaquatics.shop.identity.application.view.CurrentUserView;
import com.pwaquatics.shop.identity.domain.EmailAddress;
import com.pwaquatics.shop.identity.domain.User;
import com.pwaquatics.shop.identity.domain.UserStatus;
import com.pwaquatics.shop.identity.infrastructure.UserRepository;
import com.pwaquatics.shop.security.ShopUserPrincipal;
import com.pwaquatics.shop.security.ratelimit.RateLimiter;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Verifies credentials. Starting the session is done by the web layer ({@code SessionManager}). */
@Service
public class AuthService {

    private final UserRepository users;
    private final LoginRecorder recorder;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    /** Compared against when the account does not exist, so both cases take the same time. */
    private final String dummyHash;

    AuthService(
            UserRepository users,
            LoginRecorder recorder,
            PasswordEncoder passwordEncoder,
            RateLimiter rateLimiter,
            Clock clock) {
        this.users = users;
        this.recorder = recorder;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Checks email and password.
     *
     * <p>What an attacker can learn is limited on purpose: unknown address and wrong password
     * give the same error, and "locked" or "not verified" are only revealed after the correct
     * password. Attempts are limited per IP, per address and per account.
     *
     * @throws UnauthorizedException {@code INVALID_CREDENTIALS}
     * @throws RateLimitedException too many attempts (rate limit or temporary account lock)
     * @throws LockedException account locked by an administrator
     * @throws ForbiddenException {@code EMAIL_NOT_VERIFIED}
     */
    public ShopUserPrincipal authenticate(String rawEmail, String password, String clientIp) {
        String email = EmailAddress.normalise(rawEmail);
        rateLimiter.consume("login-ip", clientIp);
        rateLimiter.consume("login-email", email);

        Optional<User> found = users.findByEmail(email).filter(user -> user.getPasswordHash() != null);
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            throw invalidCredentials();
        }
        User user = found.get();
        Instant now = clock.instant();
        if (user.isTemporarilyLocked(now)) {
            throw new RateLimitedException(user.remainingLock(now));
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            recorder.recordFailure(user.getId(), clientIp);
            throw invalidCredentials();
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new LockedException("This account has been locked. Please contact us.");
        }
        if (!user.isEmailVerified()) {
            throw new ForbiddenException(
                    "EMAIL_NOT_VERIFIED", "Please confirm your email address before signing in.");
        }
        String upgradedHash = passwordEncoder.upgradeEncoding(user.getPasswordHash())
                ? passwordEncoder.encode(password)
                : null;
        User updated = recorder.recordSuccess(user.getId(), upgradedHash);
        return new ShopUserPrincipal(updated.getId(), updated.getRole().name());
    }

    /** The current user, or empty if the account can no longer be used (deleted or locked meanwhile). */
    @Transactional(readOnly = true)
    public Optional<CurrentUserView> currentUser(ShopUserPrincipal principal) {
        if (principal == null) {
            return Optional.empty();
        }
        return users.findById(principal.userId())
                .filter(User::isActive)
                .map(user -> new CurrentUserView(
                        user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.getRole()));
    }

    private static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("INVALID_CREDENTIALS", "Email address or password is incorrect.");
    }
}
