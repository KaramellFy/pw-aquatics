package com.pwaquatics.shop.identity.application;

import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.identity.domain.User;
import com.pwaquatics.shop.identity.domain.UserToken;
import com.pwaquatics.shop.identity.domain.UserTokenType;
import com.pwaquatics.shop.identity.infrastructure.UserTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Issues and redeems single-use tokens for emailed links. Raw tokens are never stored. */
@Service
class TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserTokenRepository tokens;
    private final Clock clock;

    TokenService(UserTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    /**
     * Creates a new token and invalidates earlier tokens of the same type for this user.
     *
     * @return the raw token for the email link (256 bits of randomness)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    String issue(User user, UserTokenType type, Duration ttl, String newEmail) {
        tokens.deleteByUserAndType(user.getId(), type);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        tokens.save(new UserToken(user, type, hash(raw), newEmail, now, now.plus(ttl)));
        return raw;
    }

    /**
     * Redeems a token exactly once.
     *
     * @throws BusinessRuleException {@code TOKEN_INVALID} if unknown, expired, already used or of another type
     */
    @Transactional(propagation = Propagation.MANDATORY)
    UserToken redeem(String rawToken, Set<UserTokenType> allowedTypes) {
        Instant now = clock.instant();
        UserToken token = tokens.findByTokenHash(hash(rawToken == null ? "" : rawToken))
                .filter(candidate -> allowedTypes.contains(candidate.getType()) && candidate.isUsable(now))
                .orElseThrow(() -> new BusinessRuleException(
                        "TOKEN_INVALID", "This link is invalid or has expired. Please request a new one."));
        token.markUsed(now);
        return token;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void deleteAllFor(User user) {
        tokens.deleteByUser(user.getId());
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
