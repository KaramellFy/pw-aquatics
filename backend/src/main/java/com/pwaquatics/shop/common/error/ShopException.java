package com.pwaquatics.shop.common.error;

/**
 * Base class for expected business errors. Each subclass maps to one HTTP status
 * in {@link com.pwaquatics.shop.common.api.ApiExceptionHandler}; {@link #code()} is the
 * stable machine-readable error code the frontend switches on (see docs/API.md §1.4).
 */
public abstract class ShopException extends RuntimeException {

    private final String code;

    protected ShopException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
