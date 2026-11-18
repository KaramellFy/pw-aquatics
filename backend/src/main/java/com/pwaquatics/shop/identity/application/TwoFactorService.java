package com.pwaquatics.shop.identity.application;

import com.pwaquatics.shop.audit.application.AuditService;
import com.pwaquatics.shop.common.crypto.Base32;
import com.pwaquatics.shop.common.crypto.SecretBox;
import com.pwaquatics.shop.common.crypto.Tokens;
import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.common.error.ForbiddenException;
import com.pwaquatics.shop.common.error.NotFoundException;
import com.pwaquatics.shop.common.error.UnauthorizedException;
import com.pwaquatics.shop.identity.application.view.RecoveryCodesView;
import com.pwaquatics.shop.identity.application.view.TwoFactorSetupView;
import com.pwaquatics.shop.identity.application.view.TwoFactorStatusView;
import com.pwaquatics.shop.identity.domain.RecoveryCode;
import com.pwaquatics.shop.identity.domain.RecoveryCodes;
import com.pwaquatics.shop.identity.domain.Totp;
import com.pwaquatics.shop.identity.domain.TwoFactor;
import com.pwaquatics.shop.identity.domain.User;
import com.pwaquatics.shop.identity.domain.UserAnonymised;
import com.pwaquatics.shop.identity.domain.UserRole;
import com.pwaquatics.shop.identity.infrastructure.RecoveryCodeRepository;
import com.pwaquatics.shop.identity.infrastructure.TwoFactorRepository;
import com.pwaquatics.shop.identity.infrastructure.UserRepository;
import com.pwaquatics.shop.notification.application.OutboxService;
import com.pwaquatics.shop.security.ratelimit.RateLimiter;
import jakarta.annotation.Nullable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two-step sign-in for administrators (ADR-015): after the password, a code from an
 * authenticator app (TOTP) or one of ten one-time recovery codes. The admin area opens only for
 * sessions that passed both steps; administrators without it set it up first.
 *
 * <p>Every check of a code is rate limited per account ({@code two-factor}); each app code is
 * accepted once only. Setting up, renewing recovery codes and using one are audited and
 * announced by email.
 */
@Service
public class TwoFactorService {

    /** Shown as the account's name in the authenticator app. */
    public static final String ISSUER = "PW Aquatics";

    private static final Logger log = LoggerFactory.getLogger(TwoFactorService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TwoFactorRepository twoFactors;
    private final RecoveryCodeRepository recoveryCodes;
    private final UserRepository users;
    private final SecretBox secretBox;
    private final AuthEmails emails;
    private final OutboxService outbox;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    private final Clock clock;

    TwoFactorService(
            TwoFactorRepository twoFactors,
            RecoveryCodeRepository recoveryCodes,
            UserRepository users,
            SecretBox secretBox,
            AuthEmails emails,
            OutboxService outbox,
            RateLimiter rateLimiter,
            AuditService audit,
            Clock clock) {
        this.twoFactors = twoFactors;
        this.recoveryCodes = recoveryCodes;
        this.users = users;
        this.secretBox = secretBox;
        this.emails = emails;
        this.outbox = outbox;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.clock = clock;
    }

    /** Whether signing in to this account asks for a second step after the password. */
    @Transactional(readOnly = true)
    public boolean isEnabled(UUID userId) {
        return twoFactors.isEnabledFor(userId);
    }

    @Transactional(readOnly = true)
    public TwoFactorStatusView status(UUID userId) {
        requireAdministrator(userId);
        Optional<TwoFactor> twoFactor = twoFactors.findByUserId(userId).filter(TwoFactor::isEnabled);
        return new TwoFactorStatusView(
                twoFactor.isPresent(),
                twoFactor.map(TwoFactor::getEnabledAt).orElse(null),
                twoFactor.isPresent() ? recoveryCodes.countUnused(userId) : 0);
    }

    /**
     * Creates a new key for an authenticator app. It is used only once the first code from it
     * was entered ({@link #confirmSetup}). Replacing a working app needs a current code (or a
     * recovery code), so a stolen session cannot move the second step to another phone.
     */
    @Transactional
    public TwoFactorSetupView startSetup(UUID userId, @Nullable String currentCode, String clientIp) {
        User user = requireAdministrator(userId);
        Instant now = clock.instant();
        byte[] secret = new byte[Totp.SECRET_BYTES];
        RANDOM.nextBytes(secret);
        String sealed = secretBox.seal(secret, context(userId));

        Optional<TwoFactor> existing = twoFactors.lockByUserId(userId);
        if (existing.isPresent()) {
            TwoFactor twoFactor = existing.get();
            if (twoFactor.isEnabled()) {
                if (currentCode == null || currentCode.isBlank()) {
                    throw new BusinessRuleException(
                            "TWO_FACTOR_CODE_REQUIRED", "Enter a code from your current app first.");
                }
                requireCode(user, twoFactor, currentCode, now, clientIp);
            }
            twoFactor.restartSetup(sealed, now);
        } else {
            twoFactors.save(TwoFactor.startSetup(userId, sealed, now));
        }
        String base32 = Base32.encode(secret);
        return new TwoFactorSetupView(base32, otpauthUri(user.getEmail(), base32));
    }

    /**
     * Turns on the new key once its first code is right and returns ten new recovery codes (the
     * old ones stop working). The caller upgrades the session and ends the user's other sessions.
     */
    @Transactional
    public RecoveryCodesView confirmSetup(UUID userId, String code, String clientIp) {
        User user = requireAdministrator(userId);
        rateLimiter.consume("two-factor", userId.toString());
        TwoFactor twoFactor = twoFactors.lockByUserId(userId)
                .filter(found -> found.getPendingSecretCiphertext() != null)
                .orElseThrow(() -> new BusinessRuleException(
                        "TWO_FACTOR_SETUP_NOT_STARTED", "Start the setup again."));
        Instant now = clock.instant();
        byte[] secret = secretBox.open(twoFactor.getPendingSecretCiphertext(), context(userId));
        long step = Totp.match(secret, normalise(code), now, Long.MIN_VALUE)
                .orElseThrow(TwoFactorService::wrongCode);

        boolean replacing = twoFactor.isEnabled();
        twoFactor.confirmSetup(step, now);
        List<String> codes = replaceRecoveryCodes(userId, now);
        audit.recordUserAction(
                userId, replacing ? "USER_TWO_FACTOR_APP_CHANGED" : "USER_TWO_FACTOR_ENABLED", clientIp);
        outbox.enqueueEmail(emails.twoFactorEnabled(user, replacing));
        return new RecoveryCodesView(codes);
    }

    /** Ten new recovery codes for a current code; the old ones stop working. */
    @Transactional
    public RecoveryCodesView renewRecoveryCodes(UUID userId, String code, String clientIp) {
        User user = requireAdministrator(userId);
        TwoFactor twoFactor = twoFactors.lockByUserId(userId)
                .filter(TwoFactor::isEnabled)
                .orElseThrow(() -> new BusinessRuleException(
                        "TWO_FACTOR_NOT_ENABLED", "Set up two-step sign-in first."));
        Instant now = clock.instant();
        requireCode(user, twoFactor, code, now, clientIp);
        List<String> codes = replaceRecoveryCodes(userId, now);
        audit.recordUserAction(userId, "USER_TWO_FACTOR_RECOVERY_CODES_RENEWED", clientIp);
        return new RecoveryCodesView(codes);
    }

    /**
     * The second step of signing in: a code from the app or an unused recovery code.
     *
     * @throws UnauthorizedException {@code TWO_FACTOR_CODE_INVALID}, or {@code TWO_FACTOR_EXPIRED}
     *     when the account can no longer sign in
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public void verifySignIn(UUID userId, String code, String clientIp) {
        User user = users.findById(userId).filter(User::isActive).orElseThrow(TwoFactorService::signInAgain);
        TwoFactor twoFactor = twoFactors.lockByUserId(userId)
                .filter(TwoFactor::isEnabled)
                .orElseThrow(TwoFactorService::signInAgain);
        try {
            requireCode(user, twoFactor, code, clock.instant(), clientIp);
        } catch (BusinessRuleException e) {
            audit.recordUserAction(userId, "USER_TWO_FACTOR_CODE_REJECTED", clientIp);
            throw new UnauthorizedException("TWO_FACTOR_CODE_INVALID", "This code is not correct.");
        }
    }

    /**
     * The sign-in was given up after too many wrong codes: whoever tried knew the password, so
     * the administrator is told by email.
     */
    @Transactional
    public void tooManyWrongCodes(UUID userId, String clientIp) {
        users.findById(userId).filter(User::isActive).ifPresent(user -> {
            audit.recordUserAction(userId, "USER_TWO_FACTOR_TOO_MANY_WRONG_CODES", clientIp);
            outbox.enqueueEmail(emails.twoFactorWrongCodes(user));
        });
    }

    /** Removes the second step when an account is deleted (same transaction as the anonymisation). */
    @EventListener
    @Transactional
    public void on(UserAnonymised event) {
        twoFactors.deleteByUserId(event.userId());
    }

    /**
     * Accepts an app code (each once) or an unused recovery code.
     *
     * @throws BusinessRuleException {@code TWO_FACTOR_CODE_INVALID}
     */
    private void requireCode(User user, TwoFactor twoFactor, String typed, Instant now, String clientIp) {
        rateLimiter.consume("two-factor", user.getId().toString());
        String code = normalise(typed);
        if (Totp.isCodeShaped(code)) {
            OptionalLong step = activeSecret(twoFactor)
                    .map(secret -> Totp.match(secret, code, now, twoFactor.getLastUsedStep()))
                    .orElse(OptionalLong.empty());
            if (step.isEmpty()) {
                throw wrongCode();
            }
            twoFactor.codeAccepted(step.getAsLong(), now);
            return;
        }
        String recovery = RecoveryCodes.normalise(typed);
        if (!RecoveryCodes.isCodeShaped(recovery)) {
            throw wrongCode();
        }
        RecoveryCode found = recoveryCodes.findUnused(user.getId(), Tokens.sha256Hex(recovery))
                .orElseThrow(TwoFactorService::wrongCode);
        found.use(now);
        recoveryCodes.flush();
        int left = recoveryCodes.countUnused(user.getId());
        audit.record(user.getId(), "USER_TWO_FACTOR_RECOVERY_CODE_USED", "USER", user.getId(),
                Map.of("recoveryCodesLeft", left), clientIp);
        outbox.enqueueEmail(emails.recoveryCodeUsed(user, left));
    }

    /**
     * The app's key, or empty if it cannot be decrypted (APP_ENCRYPTION_KEY was changed): then
     * only recovery codes work, and with one the administrator sets up the app again.
     */
    private Optional<byte[]> activeSecret(TwoFactor twoFactor) {
        try {
            return Optional.of(secretBox.open(twoFactor.getSecretCiphertext(), context(twoFactor.getUserId())));
        } catch (IllegalStateException e) {
            log.error("A two-step sign-in key could not be decrypted (user {}); was APP_ENCRYPTION_KEY changed? "
                    + "The administrator can sign in with a recovery code and set up the app again.",
                    twoFactor.getUserId());
            return Optional.empty();
        }
    }

    private List<String> replaceRecoveryCodes(UUID userId, Instant now) {
        recoveryCodes.deleteByUserId(userId);
        List<String> codes = RecoveryCodes.generate();
        recoveryCodes.saveAll(codes.stream()
                .map(code -> new RecoveryCode(userId, Tokens.sha256Hex(RecoveryCodes.normalise(code)), now))
                .toList());
        return codes;
    }

    /** Two-step sign-in is for administrators; customers sign in with password or Google. */
    private User requireAdministrator(UUID userId) {
        User user = users.findById(userId).filter(User::isActive)
                .orElseThrow(() -> new NotFoundException("Account not found."));
        if (user.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException(
                    "TWO_FACTOR_ADMINS_ONLY", "Two-step sign-in is available for administrators only.");
        }
        return user;
    }

    /** The key is bound to its account: copied to another row it does not open. */
    private static String context(UUID userId) {
        return "two-factor:" + userId;
    }

    private static String normalise(String typed) {
        return typed == null ? "" : typed.replaceAll("[\\s-]", "");
    }

    static String otpauthUri(String email, String base32Secret) {
        // "Issuer:account", with a plain colon: the form every authenticator app reads.
        String label = encode(ISSUER) + ":" + encode(email);
        return "otpauth://totp/%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d".formatted(
                label, base32Secret, encode(ISSUER), Totp.DIGITS, Totp.PERIOD_SECONDS);
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static BusinessRuleException wrongCode() {
        return new BusinessRuleException("TWO_FACTOR_CODE_INVALID", "This code is not correct.");
    }

    private static UnauthorizedException signInAgain() {
        return new UnauthorizedException("TWO_FACTOR_EXPIRED", "Please sign in again.");
    }
}
