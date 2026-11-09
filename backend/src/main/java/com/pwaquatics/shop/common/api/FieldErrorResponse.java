package com.pwaquatics.shop.common.api;

/** One invalid field inside a {@code VALIDATION_FAILED} problem response. */
public record FieldErrorResponse(String field, String code, String message) {}
