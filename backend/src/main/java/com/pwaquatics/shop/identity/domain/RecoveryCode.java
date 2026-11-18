package com.pwaquatics.shop.identity.domain;

import com.pwaquatics.shop.common.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One recovery code of an account's two-step sign-in; only its SHA-256 hash is stored. */
@Entity
@Table(name = "two_factor_recovery_codes")
public class RecoveryCode extends AbstractEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "code_hash", nullable = false, updatable = false, length = 64)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RecoveryCode() {}

    public RecoveryCode(UUID userId, String codeHash, Instant now) {
        this.userId = userId;
        this.codeHash = codeHash;
        this.createdAt = now;
    }

    public void use(Instant now) {
        if (usedAt != null) {
            throw new IllegalStateException("Recovery code already used");
        }
        this.usedAt = now;
    }
}
