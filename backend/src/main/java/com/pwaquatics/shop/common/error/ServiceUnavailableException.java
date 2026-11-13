package com.pwaquatics.shop.common.error;

/**
 * A service we depend on (e.g. the payment provider) cannot be reached or refused the call.
 * Maps to 503; the client may try again later.
 */
public class ServiceUnavailableException extends ShopException {

    public ServiceUnavailableException(String code, String message, Throwable cause) {
        super(code, message);
        initCause(cause);
    }
}
