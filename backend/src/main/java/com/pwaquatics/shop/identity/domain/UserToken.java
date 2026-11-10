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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A single-use, expiring token emailed to the user. Only its SHA-256 hash is stored. */
@Entity
@Table(name = "user_tokens")
public class UserToken extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserTokenType type;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "new_email", columnDefinition = "citext")
    private String newEmail;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserToken() {}

    public UserToken(User user, UserTokenType type, String tokenHash, String newEmail, Instant now, Instant expiresAt) {
        this.user = user;
        this.type = type;
        this.tokenHash = tokenHash;
        this.newEmail = EmailAddress.normalise(newEmail);
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed(Instant now) {
        usedAt = now;
    }

    public User getUser() {
        return user;
    }

    public UserTokenType getType() {
        return type;
    }

    public String getNewEmail() {
        return newEmail;
    }
}
