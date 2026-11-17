package com.pwaquatics.shop.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.payment.domain.Payment;
import com.pwaquatics.shop.payment.domain.PaymentRefund;
import com.pwaquatics.shop.payment.domain.PaymentRefundState;
import com.pwaquatics.shop.payment.domain.PaymentState;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The payment record follows what the provider reports, but never accepts a different amount. */
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

    @Test
    void refundsFromTheAdminCountAtOnceAndNeverExceedWhatIsLeft() {
        Payment payment = Payment.stripe(UUID.randomUUID(), "pi_4", Money.eur(1290), NOW);
        assertThat(payment.refundable()).isEqualTo(Money.eur(0)); // nothing has arrived
        assertThatThrownBy(() -> payment.refundAccepted(Money.eur(500), NOW)).isInstanceOf(IllegalStateException.class);

        payment.succeeded(Money.eur(1290), "card", NOW);
        assertThat(payment.refundable()).isEqualTo(Money.eur(1290));
        payment.refundAccepted(Money.eur(500), NOW);
        assertThat(payment.refundable()).isEqualTo(Money.eur(790));
        assertThat(payment.getStatus()).isEqualTo(PaymentState.PARTIALLY_REFUNDED);

        assertThatThrownBy(() -> payment.refundAccepted(Money.eur(791), NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payment.refundAccepted(Money.eur(0), NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payment.refundAccepted(new Money(100, "GBP"), NOW))
                .isInstanceOf(IllegalArgumentException.class);

        // The provider's report of the same total changes nothing.
        assertThat(payment.refunded(500, NOW)).isFalse();
        payment.refundAccepted(Money.eur(790), NOW);
        assertThat(payment.isFullyRefunded()).isTrue();
        assertThat(payment.refundable()).isEqualTo(Money.eur(0));
    }

    @Test
    void aRefundThatFailsLaterNoLongerCounts() {
        Payment payment = Payment.stripe(UUID.randomUUID(), "pi_5", Money.eur(1290), NOW);
        payment.succeeded(Money.eur(1290), "card", NOW);
        payment.refundAccepted(Money.eur(500), NOW);
        payment.refundAccepted(Money.eur(300), NOW);

        assertThat(payment.refundFailed(Money.eur(300), NOW)).isTrue();
        assertThat(payment.refunded()).isEqualTo(Money.eur(500));
        assertThat(payment.getStatus()).isEqualTo(PaymentState.PARTIALLY_REFUNDED);
        assertThat(payment.refundFailed(Money.eur(500), NOW)).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentState.SUCCEEDED);
        assertThat(payment.refundable()).isEqualTo(Money.eur(1290));
        assertThat(payment.refundFailed(Money.eur(500), NOW)).isFalse();
    }

    @Test
    void aRefundSucceedsOrFailsOnceButCanStillFailAfterSucceeding() {
        Payment payment = Payment.stripe(UUID.randomUUID(), "pi_6", Money.eur(1290), NOW);
        payment.succeeded(Money.eur(1290), "card", NOW);
        assertThatThrownBy(() -> PaymentRefund.requested(payment, Money.eur(0), "key-0", null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);

        PaymentRefund pending = PaymentRefund.requested(payment, Money.eur(500), "key-1", "Arrived dead", null, NOW);
        pending.answered("re_1", PaymentRefundState.PENDING, NOW);
        assertThat(pending.finished(PaymentRefundState.PENDING, null, NOW)).isFalse();
        assertThat(pending.finished(PaymentRefundState.SUCCEEDED, null, NOW)).isTrue();
        assertThat(pending.finished(PaymentRefundState.SUCCEEDED, null, NOW)).isFalse();
        assertThat(pending.finished(PaymentRefundState.FAILED, "expired_or_canceled_card", NOW)).isTrue();
        assertThat(pending.getFailureCode()).isEqualTo("expired_or_canceled_card");
        assertThat(pending.finished(PaymentRefundState.SUCCEEDED, null, NOW)).isFalse();
        assertThat(pending.getStatus()).isEqualTo(PaymentRefundState.FAILED);
    }
}
