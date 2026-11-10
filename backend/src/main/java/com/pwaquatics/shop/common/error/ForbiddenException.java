package com.pwaquatics.shop.common.error;

/** The caller is known but may not do this (e.g. email not verified yet). Maps to 403. */
public class ForbiddenException extends ShopException {

    public ForbiddenException(String code, String message) {
        super(code, message);
    }
}
