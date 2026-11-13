package com.pwaquatics.shop.identity.application;

import com.pwaquatics.shop.audit.application.Actor;
import com.pwaquatics.shop.audit.application.AuditService;
import com.pwaquatics.shop.common.error.ConflictException;
import com.pwaquatics.shop.common.error.NotFoundException;
import com.pwaquatics.shop.identity.domain.User;
import com.pwaquatics.shop.identity.domain.UserRole;
import com.pwaquatics.shop.identity.domain.UserStatus;
import com.pwaquatics.shop.identity.infrastructure.UserRepository;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Locking and unlocking customer accounts by the shop owner (docs/API.md §3.7). A locked
 * account cannot sign in ({@code ACCOUNT_LOCKED}); the caller ends its sessions after the lock
 * (see {@code SessionManager#endAllSessions}). Orders and data stay as they are.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AccountLocking {

    private final UserRepository users;
    private final AuditService audit;
    private final Clock clock;

    AccountLocking(UserRepository users, AuditService audit, Clock clock) {
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Locks a customer account. Locking an account that is already locked changes nothing.
     *
     * @param reason internal reason, kept in the audit log
     * @throws NotFoundException for unknown or deleted accounts
     * @throws ConflictException {@code ACCOUNT_NOT_LOCKABLE} for administrators (including yourself)
     */
    @Transactional
    public void lock(UUID userId, String reason, Actor actor) {
        User user = customer(userId);
        if (user.getStatus() == UserStatus.LOCKED) {
            return;
        }
        user.lock(clock.instant());
        Map<String, Object> details = new LinkedHashMap<>();
        if (reason != null) {
            details.put("reason", reason);
        }
        audit.record(actor, "ACCOUNT_LOCKED_BY_SHOP", "USER", userId, details);
    }

    /** Unlocks a customer account. Unlocking an active account changes nothing. */
    @Transactional
    public void unlock(UUID userId, Actor actor) {
        User user = customer(userId);
        if (user.getStatus() != UserStatus.LOCKED) {
            return;
        }
        user.unlock(clock.instant());
        audit.record(actor, "ACCOUNT_UNLOCKED_BY_SHOP", "USER", userId, Map.of());
    }

    private User customer(UUID userId) {
        User user = users.findById(userId)
                .filter(found -> found.getStatus() != UserStatus.ANONYMISED)
                .orElseThrow(() -> new NotFoundException("Customer not found."));
        if (user.getRole() != UserRole.CUSTOMER) {
            throw new ConflictException(
                    "ACCOUNT_NOT_LOCKABLE", "Administrator accounts cannot be locked here.");
        }
        return user;
    }
}
