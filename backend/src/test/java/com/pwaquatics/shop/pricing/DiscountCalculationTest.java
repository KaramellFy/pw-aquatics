package com.pwaquatics.shop.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pwaquatics.shop.pricing.domain.Discount;
import com.pwaquatics.shop.pricing.domain.DiscountOutcome;
import com.pwaquatics.shop.pricing.domain.DiscountType;
import com.pwaquatics.shop.pricing.domain.PriceCalculator;
import com.pwaquatics.shop.pricing.domain.PricedLine;
import com.pwaquatics.shop.pricing.domain.Totals;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Coupon discounts in the price calculator (docs/ARCHITECTURE.md, price calculation). */
class DiscountCalculationTest {

    private static Discount percent(long bps) {
        return new Discount(DiscountType.PERCENTAGE, bps, null, 0);
    }

    @Test
    void takesAPercentageOffTheGoodsAndReducesTheVatWithIt() {
        Totals totals = PriceCalculator.totals(List.of(new PricedLine(1000, 2, 1900)), 590, percent(1000));

        assertThat(totals.discountOutcome()).isEqualTo(DiscountOutcome.APPLIED);
        assertThat(totals.subtotal().amount()).isEqualTo(2000);
        assertThat(totals.discount().amount()).isEqualTo(200);
        assertThat(totals.shipping().amount()).isEqualTo(590);
        assertThat(totals.total().amount()).isEqualTo(2390);
        // 23.90 gross at 19 % contains 3.82 (3.8160…)
        assertThat(totals.includedTax().amount()).isEqualTo(382);
        assertThat(totals.lineDiscountCents()).containsExactly(200L);
        assertThat(totals.couponSaving().amount()).isEqualTo(200);
    }

    @Test
    void roundsAPercentageHalfUpAndRespectsTheCap() {
        // 15 % of 9.99 = 1.4985 → 1.50
        assertThat(PriceCalculator.totals(List.of(new PricedLine(999, 1, 1900)), 0, percent(1500))
                        .discount().amount())
                .isEqualTo(150);

        Discount capped = new Discount(DiscountType.PERCENTAGE, 5000, 1000L, 0);
        assertThat(PriceCalculator.totals(List.of(new PricedLine(10_000, 1, 1900)), 0, capped)
                        .discount().amount())
                .isEqualTo(1000);
    }

    @Test
    void aFixedAmountNeverExceedsTheGoods() {
        Discount fiveEuros = new Discount(DiscountType.FIXED_AMOUNT, 500, null, 0);
        Totals small = PriceCalculator.totals(List.of(new PricedLine(390, 1, 1900)), 590, fiveEuros);

        assertThat(small.discount().amount()).isEqualTo(390);
        assertThat(small.total().amount()).isEqualTo(590);
        // Only the shipping is left; it carries the VAT of the goods' rate: 5.90 → 0.94
        assertThat(small.includedTax().amount()).isEqualTo(94);
    }

    @Test
    void splitsTheDiscountOverTheLinesSoEveryRateIsReducedByItsShare() {
        // 30.00 at 19 % and 10.00 at 7 %; 4.00 off → 3.00 and 1.00
        Discount fourEuros = new Discount(DiscountType.FIXED_AMOUNT, 400, null, 0);
        Totals totals = PriceCalculator.totals(
                List.of(new PricedLine(3000, 1, 1900), new PricedLine(1000, 1, 700)), 0, fourEuros);

        assertThat(totals.lineDiscountCents()).containsExactly(300L, 100L);
        // 27.00 at 19 % → 4.31; 9.00 at 7 % → 0.59
        assertThat(totals.includedTax().amount()).isEqualTo(431 + 59);
    }

    @Test
    void leftOverCentsGoToTheLargestRemaindersSoTheSharesAddUpExactly() {
        // 1.00 off three equal lines: 33 + 33 + 33 = 99, the extra cent goes to the first line.
        Discount oneEuro = new Discount(DiscountType.FIXED_AMOUNT, 100, null, 0);
        Totals totals = PriceCalculator.totals(
                List.of(new PricedLine(500, 1, 1900), new PricedLine(500, 1, 1900), new PricedLine(500, 1, 1900)),
                0,
                oneEuro);

        assertThat(totals.lineDiscountCents()).containsExactly(34L, 33L, 33L);
        assertThat(totals.lineDiscountCents().stream().mapToLong(Long::longValue).sum()).isEqualTo(100);
    }

    @Test
    void reducedProductsAreNeitherDiscountedNorPartOfThePercentageBase() {
        Totals totals = PriceCalculator.totals(
                List.of(new PricedLine(2000, 1, 1900, true), new PricedLine(3000, 1, 1900, false)), 0, percent(1000));

        assertThat(totals.discount().amount()).isEqualTo(200);
        assertThat(totals.lineDiscountCents()).containsExactly(200L, 0L);
    }

    @Test
    void doesNotApplyWhenEverythingIsAlreadyReduced() {
        Totals totals = PriceCalculator.totals(List.of(new PricedLine(2000, 1, 1900, false)), 0, percent(1000));

        assertThat(totals.discountOutcome()).isEqualTo(DiscountOutcome.NOTHING_ELIGIBLE);
        assertThat(totals.discount().amount()).isZero();
        assertThat(totals.total().amount()).isEqualTo(2000);
    }

    @Test
    void theMinimumOrderValueCountsAllGoodsBeforeTheDiscount() {
        Discount fromFifty = new Discount(DiscountType.PERCENTAGE, 1000, null, 5000);

        Totals below = PriceCalculator.totals(List.of(new PricedLine(4999, 1, 1900)), 590, fromFifty);
        assertThat(below.discountOutcome()).isEqualTo(DiscountOutcome.MIN_ORDER_NOT_MET);
        assertThat(below.total().amount()).isEqualTo(4999 + 590);

        // A reduced line counts towards the minimum but is not discounted.
        Totals reached = PriceCalculator.totals(
                List.of(new PricedLine(3000, 1, 1900), new PricedLine(2000, 1, 1900, false)), 590, fromFifty);
        assertThat(reached.discountOutcome()).isEqualTo(DiscountOutcome.APPLIED);
        assertThat(reached.discount().amount()).isEqualTo(300);
    }

    @Test
    void freeShippingWaivesTheShippingPriceAndLeavesTheGoodsAlone() {
        Discount freeShipping = new Discount(DiscountType.FREE_SHIPPING, 0, null, 3000);

        Totals totals = PriceCalculator.totals(List.of(new PricedLine(3000, 1, 1900, false)), 990, freeShipping);
        assertThat(totals.discountOutcome()).isEqualTo(DiscountOutcome.APPLIED);
        assertThat(totals.discount().amount()).isZero();
        assertThat(totals.shipping().amount()).isZero();
        assertThat(totals.shippingWaived().amount()).isEqualTo(990);
        assertThat(totals.couponSaving().amount()).isEqualTo(990);
        assertThat(totals.total().amount()).isEqualTo(3000);

        Totals tooSmall = PriceCalculator.totals(List.of(new PricedLine(2999, 1, 1900)), 990, freeShipping);
        assertThat(tooSmall.discountOutcome()).isEqualTo(DiscountOutcome.MIN_ORDER_NOT_MET);
        assertThat(tooSmall.shipping().amount()).isEqualTo(990);
    }

    @Test
    void withoutACouponNothingChanges() {
        Totals totals = PriceCalculator.totals(List.of(new PricedLine(1000, 1, 1900)), 590);

        assertThat(totals.discountOutcome()).isEqualTo(DiscountOutcome.NONE);
        assertThat(totals.discount().amount()).isZero();
        assertThat(totals.lineDiscountCents()).containsExactly(0L);
    }

    @Test
    void rejectsTermsThatDoNotFitTheType() {
        assertThatThrownBy(() -> new Discount(DiscountType.PERCENTAGE, 0, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Discount(DiscountType.PERCENTAGE, 10_001, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Discount(DiscountType.FIXED_AMOUNT, 500, 100L, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Discount(DiscountType.FREE_SHIPPING, 1, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Discount(DiscountType.PERCENTAGE, 1000, null, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
