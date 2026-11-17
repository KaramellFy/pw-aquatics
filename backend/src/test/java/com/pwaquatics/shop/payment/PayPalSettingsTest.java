package com.pwaquatics.shop.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pwaquatics.shop.common.config.AppProperties.PayPal;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.payment.application.PayPalAmounts;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** PayPal's settings and its way of writing amounts. */
class PayPalSettingsTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Test
    void payPalIsOfferedOnlyWhenAllThreeValuesAreSet() {
        PayPal none = new PayPal("", " ", null, "sandbox", TIMEOUT);
        assertThat(none.isConfigured()).isFalse();
        assertThat(none.isCompleteOrEmpty()).isTrue();

        PayPal half = new PayPal("id", "secret", "", "sandbox", TIMEOUT);
        assertThat(half.isConfigured()).isFalse();
        assertThat(half.isCompleteOrEmpty()).isFalse();

        assertThat(new PayPal("id", "secret", "WH", "sandbox", TIMEOUT).isConfigured()).isTrue();
    }

    @Test
    void sandboxUnlessLiveIsChosen() {
        assertThat(new PayPal("id", "secret", "WH", "sandbox", TIMEOUT).apiBase())
                .hasToString("https://api-m.sandbox.paypal.com");
        PayPal live = new PayPal("id", "secret", "WH", "live", TIMEOUT);
        assertThat(live.isSandbox()).isFalse();
        assertThat(live.apiBase()).hasToString("https://api-m.paypal.com");
    }

    @Test
    void amountsAreWrittenAsPayPalExpects() {
        assertThat(PayPalAmounts.value(new Money(1290, "EUR"))).isEqualTo("12.90");
        assertThat(PayPalAmounts.value(new Money(5, "EUR"))).isEqualTo("0.05");
        assertThat(PayPalAmounts.money("12.90", "eur")).isEqualTo(new Money(1290, "EUR"));
        assertThat(PayPalAmounts.money("12.9", "EUR")).isEqualTo(new Money(1290, "EUR"));
        assertThat(PayPalAmounts.money("100", "EUR")).isEqualTo(new Money(10000, "EUR"));
        assertThatThrownBy(() -> PayPalAmounts.money("12.905", "EUR")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PayPalAmounts.money("twelve", "EUR")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PayPalAmounts.money(null, "EUR")).isInstanceOf(IllegalArgumentException.class);
    }
}
