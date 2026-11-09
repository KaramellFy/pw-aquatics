package com.pwaquatics.shop.common.error;

/** A requested resource does not exist (or is not visible to the caller). Maps to 404. */
public class NotFoundException extends ShopException {

    public NotFoundException(String message) {
        super("NOT_FOUND", message);
    }
}
