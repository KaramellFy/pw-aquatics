package com.pwaquatics.shop.common.error;

/** The request conflicts with the current state (e.g. stock, duplicates). Maps to 409. */
public class ConflictException extends ShopException {

    public ConflictException(String code, String message) {
        super(code, message);
    }
}
