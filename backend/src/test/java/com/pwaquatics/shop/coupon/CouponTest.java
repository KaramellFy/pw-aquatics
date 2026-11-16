package com.pwaquatics.shop.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.coupon.domain.Coupon;
import com.pwaquatics.shop.coupon.domain.CouponCode;
import com.pwaquatics.shop.coupon.domain.CouponData;
import com.pwaquatics.shop.coupon.domain.CouponProblem;
import com.pwaquatics.shop.coupon.domain.CouponStatus;
import com.pwaquatics.shop.pricing.domain.DiscountType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CouponTest {

    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");
    private static final Instant START = Instant.parse("2026-11-20T00:00:00Z");
    private static final Instant END = Instant.parse("2026-12-01T00:00:00Z");

    private static CouponData data(boolean active, Instant startsAt, Instant endsAt, Integer perCustomer) {
        return new CouponData("Winter", DiscountType.PERCENTAGE, 1000, 0, null, startsAt, endsAt, 100, perCustomer,
                active);
    }

    @Test
    void knowsWhetherItCanBeUsedAtAGivenTime() {
        Coupon coupon = Coupon.create("WINTER10", data(true, START, END, null), null, NOW);

        assertThat(coupon.statusAt(NOW)).isEqualTo(CouponStatus.SCHEDULED);
        assertThat(coupon.problemAt(NOW, null)).contains(CouponProblem.INVALID);
        assertThat(coupon.statusAt(START)).isEqualTo(CouponStatus.ACTIVE);
        assertThat(coupon.problemAt(START, 0L)).isEmpty();
        // The end is exclusive: the coupon works up to the moment before it.
        assertThat(coupon.statusAt(END.minusSeconds(1))).isEqualTo(CouponStatus.ACTIVE);
        assertThat(coupon.statusAt(END)).isEqualTo(CouponStatus.EXPIRED);
        assertThat(coupon.problemAt(END, 0L)).contains(CouponProblem.EXPIRED);
    }

    @Test
    void aSwitchedOffCouponIsSimplyInvalid() {
        Coupon coupon = Coupon.create("OFF10", data(false, null, null, null), null, NOW);

        assertThat(coupon.statusAt(NOW)).isEqualTo(CouponStatus.INACTIVE);
        assertThat(coupon.problemAt(NOW, null)).contains(CouponProblem.INVALID);
    }

    @Test
    void countsTheCustomersOwnUses() {
        Coupon coupon = Coupon.create("ONCE", data(true, null, null, 1), null, NOW);

        assertThat(coupon.problemAt(NOW, null)).isEmpty();
        assertThat(coupon.problemAt(NOW, 0L)).isEmpty();
        assertThat(coupon.problemAt(NOW, 1L)).contains(CouponProblem.ALREADY_USED);
    }

    @Test
    void normalisesAndChecksCodes() {
        assertThat(CouponCode.normalise("  spring-10 ")).isEqualTo("SPRING-10");
        assertThat(CouponCode.isValid("SPRING-10")).isTrue();
        assertThat(CouponCode.isValid("AB")).isFalse();
        assertThat(CouponCode.isValid("-START")).isFalse();
        assertThat(CouponCode.isValid("WITH SPACE")).isFalse();
        assertThat(CouponCode.isValid("A".repeat(33))).isFalse();
    }

    @Test
    void rejectsTermsThatDoNotFit() {
        assertThatThrownBy(() -> new CouponData(null, DiscountType.PERCENTAGE, 0, 0, null, null, null, null, null, true))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "COUPON_VALUE_INVALID");
        assertThatThrownBy(() -> new CouponData(null, DiscountType.FIXED_AMOUNT, 500, 0, 100L, null, null, null, null,
                        true))
                .hasFieldOrPropertyWithValue("code", "COUPON_VALUE_INVALID");
        assertThatThrownBy(() -> new CouponData(null, DiscountType.FREE_SHIPPING, 0, 0, null, END, START, null, null,
                        true))
                .hasFieldOrPropertyWithValue("code", "RANGE_INVALID");
    }
}
