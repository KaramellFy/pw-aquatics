package com.pwaquatics.shop.common.error;

/** The account was locked by an administrator. Maps to 423. */
public class LockedException extends ShopException {

    public LockedException(String message) {
        super("ACCOUNT_LOCKED", message);
    }
}
