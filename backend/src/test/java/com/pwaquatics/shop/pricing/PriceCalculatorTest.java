package com.pwaquatics.shop.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pwaquatics.shop.pricing.domain.PriceCalculator;
import com.pwaquatics.shop.pricing.domain.PricedLine;
import com.pwaquatics.shop.pricing.domain.Totals;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriceCalculatorTest {

    @Test
    void sumsLinesAndExtractsTheIncludedVat() {
        Totals totals = PriceCalculator.totals(List.of(new PricedLine(390, 3, 1900), new PricedLine(3490, 1, 1900)));

        assertThat(totals.subtotal().amount()).isEqualTo(4660);
        assertThat(totals.total().amount()).isEqualTo(4660);
        // 46.60 gross at 19 % contains 7.44 VAT (7.4403…)
        assertThat(totals.includedTax().amount()).isEqualTo(744);
        assertThat(totals.total().currency()).isEqualTo("EUR");
    }

    @Test
    void roundsVatHalfUpOncePerRateNotPerLine() {
        // Each line alone would round 0.4790 → 0.48 (3 × 48 = 144); together 9.00 gross contains 1.44.
        // With three lines of 1.00: per line 0.16 (0.1597) → 48, together 3.00 → 0.48.
        Totals totals = PriceCalculator.totals(
                List.of(new PricedLine(100, 1, 1900), new PricedLine(100, 1, 1900), new PricedLine(100, 1, 1900)));
        assertThat(totals.includedTax().amount()).isEqualTo(48);

        assertThat(PriceCalculator.includedTaxCents(119, 1900)).isEqualTo(19);
        assertThat(PriceCalculator.includedTaxCents(107, 700)).isEqualTo(7);
        assertThat(PriceCalculator.includedTaxCents(600, 1900)).isEqualTo(96); // 95.798… rounds up
        assertThat(PriceCalculator.includedTaxCents(1, 1900)).isZero();
    }

    @Test
    void handlesMixedTaxRatesAndEmptyCarts() {
        Totals mixed = PriceCalculator.totals(List.of(new PricedLine(1190, 1, 1900), new PricedLine(1070, 2, 700)));
        assertThat(mixed.subtotal().amount()).isEqualTo(3330);
        assertThat(mixed.includedTax().amount()).isEqualTo(190 + 140);

        Totals empty = PriceCalculator.totals(List.of());
        assertThat(empty.total().amount()).isZero();
        assertThat(empty.includedTax().amount()).isZero();
    }

    @Test
    void addsShippingAndTaxesItLikeTheGoods() {
        // 10.00 goods + 5.90 shipping, everything at 19 %: 15.90 gross contains 2.54 VAT.
        Totals totals = PriceCalculator.totals(List.of(new PricedLine(1000, 1, 1900)), 590);
        assertThat(totals.subtotal().amount()).isEqualTo(1000);
        assertThat(totals.shipping().amount()).isEqualTo(590);
        assertThat(totals.total().amount()).isEqualTo(1590);
        assertThat(totals.includedTax().amount()).isEqualTo(254);

        // Mixed rates: 30.00 at 19 % and 10.00 at 7 %; 10.00 shipping is split 7.50 / 2.50.
        Totals mixed = PriceCalculator.totals(
                List.of(new PricedLine(3000, 1, 1900), new PricedLine(1000, 1, 700)), 1000);
        assertThat(mixed.total().amount()).isEqualTo(5000);
        assertThat(mixed.includedTax().amount())
                .isEqualTo(PriceCalculator.includedTaxCents(3750, 1900) + PriceCalculator.includedTaxCents(1250, 700));

        // A cent that cannot be split goes to the highest rate; nothing is lost.
        Totals uneven = PriceCalculator.totals(
                List.of(new PricedLine(100, 1, 1900), new PricedLine(100, 1, 700), new PricedLine(100, 1, 0)), 100);
        assertThat(uneven.includedTax().amount())
                .isEqualTo(PriceCalculator.includedTaxCents(134, 1900) + PriceCalculator.includedTaxCents(133, 700));

        assertThat(PriceCalculator.totals(List.of(new PricedLine(1000, 1, 1900))).shipping().amount()).isZero();
    }

    @Test
    void rejectsNonsenseAndOverflowInsteadOfWrappingAround() {
        assertThatThrownBy(() -> PriceCalculator.totals(List.of(), 500)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PriceCalculator.totals(List.of(new PricedLine(100, 1, 1900)), -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PricedLine(-1, 1, 1900)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PricedLine(100, 0, 1900)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PricedLine(Long.MAX_VALUE, 2, 1900).totalCents())
                .isInstanceOf(ArithmeticException.class);
    }
}
