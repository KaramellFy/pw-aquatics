package com.pwaquatics.shop.admin.application;

import com.pwaquatics.shop.admin.application.view.CustomerDetailView;
import com.pwaquatics.shop.admin.application.view.CustomerSummaryView;
import com.pwaquatics.shop.admin.infrastructure.CustomerQueries;
import com.pwaquatics.shop.audit.application.Actor;
import com.pwaquatics.shop.common.error.NotFoundException;
import com.pwaquatics.shop.identity.application.AccountLocking;
import com.pwaquatics.shop.identity.domain.UserStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer accounts for the shop owner (docs/API.md §3.7): who they are, what they ordered and
 * spent, and locking an account. Figures come from the read model ({@link CustomerQueries});
 * locking is done by the identity module ({@link AccountLocking}).
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminCustomerService {

    private final CustomerQueries queries;
    private final AccountLocking locking;

    AdminCustomerService(CustomerQueries queries, AccountLocking locking) {
        this.queries = queries;
        this.locking = locking;
    }

    /**
     * @param text part of the email address or name, or null
     * @param status {@code ACTIVE} or {@code LOCKED}, or null for both
     */
    @Transactional(readOnly = true)
    public Page<CustomerSummaryView> list(String text, UserStatus status, int page, int size) {
        return queries.search(text, status, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public CustomerDetailView get(UUID id) {
        return queries.find(id).orElseThrow(() -> new NotFoundException("Customer not found."));
    }

    /**
     * Locks the account; the caller ends its sessions. Not one transaction on purpose: the lock
     * is committed by {@link AccountLocking} before the read model (plain SQL) reads it back.
     */
    public CustomerDetailView lock(UUID id, String reason, Actor actor) {
        locking.lock(id, reason, actor);
        return get(id);
    }

    /** Unlocks the account (committed before it is read back, see {@link #lock}). */
    public CustomerDetailView unlock(UUID id, Actor actor) {
        locking.unlock(id, actor);
        return get(id);
    }
}
