package com.pwaquatics.shop.common.error;

/** Authentication failed (e.g. wrong credentials). Maps to 401. */
public class UnauthorizedException extends ShopException {

    public UnauthorizedException(String code, String message) {
        super(code, message);
    }
}
