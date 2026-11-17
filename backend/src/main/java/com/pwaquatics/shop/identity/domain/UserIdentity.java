package com.pwaquatics.shop.identity.domain;

import com.pwaquatics.shop.common.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * An account at a sign-in provider (Google) linked to a shop account. It is found by the
 * provider's stable subject id; the address is only kept to show the customer which account is
 * connected.
 */
@Entity
@Table(name = "user_identities")
public class UserIdentity extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private IdentityProvider provider;

    @Column(nullable = false, length = 255, updatable = false)
    private String subject;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    protected UserIdentity() {}

    public UserIdentity(User user, IdentityProvider provider, String subject, String email, Instant now) {
        this.user = user;
        this.provider = provider;
        this.subject = subject;
        this.email = email;
        this.createdAt = now;
        this.lastUsedAt = now;
    }

    /** Signed in with it again; the provider's address may have changed since. */
    public void used(String currentEmail, Instant now) {
        this.email = currentEmail;
        this.lastUsedAt = now;
    }

    public User getUser() {
        return user;
    }

    public IdentityProvider getProvider() {
        return provider;
    }

    public String getSubject() {
        return subject;
    }

    public String getEmail() {
        return email;
    }
}
