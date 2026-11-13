package com.pwaquatics.shop.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwaquatics.shop.common.config.AppProperties.Stripe;
import org.junit.jupiter.api.Test;

/** The application refuses to start with a half-configured or mixed test/live Stripe account. */
class StripeSettingsTest {

    @Test
    void cardPaymentIsOfferedOnlyWhenAllThreeValuesAreSet() {
        Stripe none = new Stripe("", " ", null);
        assertThat(none.isConfigured()).isFalse();
        assertThat(none.isCompleteOrEmpty()).isTrue();
        assertThat(none.isOneMode()).isTrue();

        Stripe half = new Stripe("sk_test_x", "pk_test_x", "");
        assertThat(half.isConfigured()).isFalse();
        assertThat(half.isCompleteOrEmpty()).isFalse();

        assertThat(new Stripe("sk_test_x", "pk_test_x", "whsec_x").isConfigured()).isTrue();
    }

    @Test
    void testAndLiveKeysAreNotMixed() {
        assertThat(new Stripe("sk_test_x", "pk_test_x", "whsec_x").isOneMode()).isTrue();
        assertThat(new Stripe("rk_live_x", "pk_live_x", "whsec_x").isOneMode()).isTrue();
        assertThat(new Stripe("sk_live_x", "pk_test_x", "whsec_x").isOneMode()).isFalse();
        assertThat(new Stripe("sk_test_x", "pk_live_x", "whsec_x").isOneMode()).isFalse();
    }
}
