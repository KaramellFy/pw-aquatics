package com.pwaquatics.shop.common.error;

/** A well-formed request that violates a business rule (e.g. invalid coupon). Maps to 422. */
public class BusinessRuleException extends ShopException {

    public BusinessRuleException(String code, String message) {
        super(code, message);
    }
}
