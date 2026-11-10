package com.pwaquatics.shop.identity.domain;

public enum UserStatus {
    ACTIVE,
    /** Locked by an administrator (not the temporary lock after failed logins). */
    LOCKED,
    /** Personal data removed after account deletion; the row remains for order history. */
    ANONYMISED
}
