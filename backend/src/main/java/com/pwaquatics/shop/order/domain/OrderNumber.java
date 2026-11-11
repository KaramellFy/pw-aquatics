package com.pwaquatics.shop.order.domain;

/** Human-readable order numbers such as {@code PW-2027-000123}; also the bank-transfer reference. */
public final class OrderNumber {

    private OrderNumber() {}

    /**
     * @param year calendar year in the shop's time zone
     * @param sequence the next value of the database sequence (positive)
     */
    public static String of(int year, long sequence) {
        if (sequence < 1) {
            throw new IllegalArgumentException("Order sequence must be positive");
        }
        return "PW-%d-%06d".formatted(year, sequence);
    }
}
