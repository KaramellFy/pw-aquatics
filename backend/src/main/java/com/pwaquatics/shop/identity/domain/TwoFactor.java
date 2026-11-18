package com.pwaquatics.shop.identity.domain;

import com.pwaquatics.shop.common.persistence.AbstractEntity;
import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Two-step sign-in of one account (docs/adr/015-two-step-sign-in-for-administrators.md): the key
 * shared with the authenticator app, kept encrypted ({@code SecretBox}). A new key waits as
 * "pending" until the first code from it is entered, so setting up a new phone does not replace
 * the old one before the new one works.
 */
@Entity
@Table(name = "user_two_factor")
public class TwoFactor extends AbstractEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "secret_ciphertext")
    private String secretCiphertext;

    @Column(name = "pending_secret_ciphertext")
    private String pendingSecretCiphertext;

    @Column(name = "enabled_at")
    private Instant enabledAt;

    @Column(name = "last_used_step", nullable = false)
    private long lastUsedStep;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected TwoFactor() {}

    public static TwoFactor startSetup(UUID userId, String sealedSecret, Instant now) {
        TwoFactor twoFactor = new TwoFactor();
        twoFactor.userId = userId;
        twoFactor.pendingSecretCiphertext = sealedSecret;
        twoFactor.createdAt = now;
        twoFactor.updatedAt = now;
        return twoFactor;
    }

    /** A new key to set up; replaces a pending one, never the key in use. */
    public void restartSetup(String sealedSecret, Instant now) {
        this.pendingSecretCiphertext = sealedSecret;
        this.updatedAt = now;
    }

    /** The first code from the pending key was right: it becomes the key in use. */
    public void confirmSetup(long acceptedStep, Instant now) {
        if (pendingSecretCiphertext == null) {
            throw new IllegalStateException("No key waiting for confirmation");
        }
        this.secretCiphertext = pendingSecretCiphertext;
        this.pendingSecretCiphertext = null;
        this.enabledAt = now;
        this.lastUsedStep = acceptedStep;
        this.updatedAt = now;
    }

    /** A code was accepted; it and older ones are not accepted again. */
    public void codeAccepted(long step, Instant now) {
        this.lastUsedStep = step;
        this.updatedAt = now;
    }

    public boolean isEnabled() {
        return secretCiphertext != null;
    }

    public UUID getUserId() {
        return userId;
    }

    public @Nullable String getSecretCiphertext() {
        return secretCiphertext;
    }

    public @Nullable String getPendingSecretCiphertext() {
        return pendingSecretCiphertext;
    }

    public @Nullable Instant getEnabledAt() {
        return enabledAt;
    }

    public long getLastUsedStep() {
        return lastUsedStep;
    }
}
