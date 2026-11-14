package com.pwaquatics.shop.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.payment.domain.Payment;
import com.pwaquatics.shop.payment.domain.PaymentState;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The payment record follows what Stripe reports, but never accepts a different amount. */
class PaymentTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    @Test
    void aPaymentSucceedsOnceAndOnlyForItsOwnAmount() {
        Payment payment = Payment.stripe(UUID.randomUUID(), "pi_1", Money.eur(1290), NOW);
        assertThat(payment.getStatus()).isEqualTo(PaymentState.CREATED);

        assertThatThrownBy(() -> payment.succeeded(Money.eur(1289), "card", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payment.succeeded(new Money(1290, "GBP"), "card", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentState.CREATED);

        assertThat(payment.succeeded(Money.eur(1290), "card", NOW)).isTrue();
        assertThat(payment.succeeded(Money.eur(1290), "card", NOW)).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentState.SUCCEEDED);
    }

    @Test
    void lateReportsDoNotUndoAPayment() {
        Payment payment = Payment.stripe(UUID.randomUUID(), "pi_2", Money.eur(1290), NOW);
        payment.failed("card_declined", NOW);
        assertThat(payment.getStatus()).isEqualTo(PaymentState.FAILED);
        assertThat(payment.getFailureCode()).isEqualTo("card_declined");
        payment.processing(NOW);
        assertThat(payment.getStatus()).isEqualTo(PaymentState.PENDING);

        payment.succeeded(Money.eur(1290), "card", NOW);
        payment.failed("card_declined", NOW);
        payment.cancelled(NOW);
        payment.processing(NOW);
        assertThat(payment.getStatus()).isEqualTo(PaymentState.SUCCEEDED);
        assertThat(payment.getFailureCode()).isNull();
    }

    @Test
    void refundsAddUpToTheAmountPaid() {
        Payment payment = Payment.stripe(UUID.randomUUID(), "pi_3", Money.eur(1290), NOW);
        assertThat(payment.refunded(500, NOW)).isFalse(); // nothing paid yet

        payment.succeeded(Money.eur(1290), "card", NOW);
        assertThat(payment.refunded(500, NOW)).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentState.PARTIALLY_REFUNDED);
        assertThat(payment.refunded(500, NOW)).isFalse(); // the same report again
        assertThat(payment.refunded(1290, NOW)).isTrue();
        assertThat(payment.isFullyRefunded()).isTrue();
        assertThat(payment.refunded()).isEqualTo(Money.eur(1290));
        assertThatThrownBy(() -> payment.refunded(1300, NOW)).isInstanceOf(IllegalArgumentException.class);
    }
}
