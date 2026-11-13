package com.pwaquatics.shop.common.error;

/** The request itself is unacceptable (e.g. a webhook with a wrong signature). Maps to 400. */
public class BadRequestException extends ShopException {

    public BadRequestException(String code, String message) {
        super(code, message);
    }
}
