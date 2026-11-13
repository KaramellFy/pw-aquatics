package com.pwaquatics.shop.identity.domain;

import com.pwaquatics.shop.common.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;

/** A customer or administrator account. */
@Entity
@Table(name = "users")
public class User extends AbstractEntity {

    @Column(nullable = false, unique = true, columnDefinition = "citext")
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(length = 40)
    private String phone;

    @Column(nullable = false, length = 5)
    private String locale = "en";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.CUSTOMER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "marketing_opt_in", nullable = false)
    private boolean marketingOptIn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected User() {}

    /** A new, unverified customer account. {@code email} is normalised here. */
    public static User register(
            String email, String passwordHash, String firstName, String lastName, String locale, Instant now) {
        User user = new User();
        user.email = EmailAddress.normalise(email);
        user.passwordHash = passwordHash;
        user.firstName = firstName;
        user.lastName = lastName;
        user.locale = "de".equals(locale) ? "de" : "en";
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }

    /** An administrator; created verified because the address comes from the deployment configuration. */
    public static User administrator(String email, String passwordHash, Instant now) {
        User user = register(email, passwordHash, "Shop", "Admin", "en", now);
        user.role = UserRole.ADMIN;
        user.emailVerifiedAt = now;
        return user;
    }

    // ------------------------------------------------------------------ rules

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    /** Temporarily locked after too many failed logins. */
    public boolean isTemporarilyLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public Duration remainingLock(Instant now) {
        return isTemporarilyLocked(now) ? Duration.between(now, lockedUntil) : Duration.ZERO;
    }

    /** Counts a failed login; after {@code maxAttempts} in a row the account is locked for {@code lockDuration}. */
    public void recordFailedLogin(Instant now, int maxAttempts, Duration lockDuration) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            failedLoginAttempts = 0;
        }
        updatedAt = now;
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
        updatedAt = now;
    }

    public void verifyEmail(Instant now) {
        if (emailVerifiedAt == null) {
            emailVerifiedAt = now;
            updatedAt = now;
        }
    }

    /** Switches to a new, already verified address (the user proved ownership via an emailed link). */
    public void changeEmail(String newEmail, Instant now) {
        email = EmailAddress.normalise(newEmail);
        emailVerifiedAt = now;
        updatedAt = now;
    }

    /** Sets a new password hash and clears any temporary lock. */
    public void changePassword(String newPasswordHash, Instant now) {
        passwordHash = newPasswordHash;
        failedLoginAttempts = 0;
        lockedUntil = null;
        updatedAt = now;
    }

    public void updateProfile(String firstName, String lastName, String phone, boolean marketingOptIn, Instant now) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.marketingOptIn = marketingOptIn;
        this.updatedAt = now;
    }

    public void setLocale(String locale, Instant now) {
        this.locale = "de".equals(locale) ? "de" : "en";
        this.updatedAt = now;
    }

    /** Locked by the shop: the account cannot sign in until it is unlocked. */
    public void lock(Instant now) {
        if (status != UserStatus.ACTIVE) {
            throw new IllegalStateException("Only active accounts can be locked");
        }
        status = UserStatus.LOCKED;
        updatedAt = now;
    }

    /** Lifts a lock by the shop and any temporary lock after failed logins. */
    public void unlock(Instant now) {
        if (status != UserStatus.LOCKED) {
            throw new IllegalStateException("Only locked accounts can be unlocked");
        }
        status = UserStatus.ACTIVE;
        failedLoginAttempts = 0;
        lockedUntil = null;
        updatedAt = now;
    }

    /**
     * Removes all personal data but keeps the row, so orders stay linked for the statutory
     * retention period (docs/DATABASE.md §6). The account can never be used again.
     */
    public void anonymise(Instant now) {
        email = "deleted-" + getId() + "@anonymised.invalid";
        passwordHash = null;
        firstName = "Deleted";
        lastName = "User";
        phone = null;
        marketingOptIn = false;
        status = UserStatus.ANONYMISED;
        failedLoginAttempts = 0;
        lockedUntil = null;
        updatedAt = now;
    }

    // ------------------------------------------------------------------ accessors

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getPhone() {
        return phone;
    }

    public String getLocale() {
        return locale;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public boolean isMarketingOptIn() {
        return marketingOptIn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
