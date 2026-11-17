package com.pwaquatics.shop.payment.domain;

import com.pwaquatics.shop.common.money.Money;
import java.util.UUID;

/**
 * The money refunded for a paid order changed: a refund from the admin order page or the
 * provider's dashboard, or a refund that failed afterwards.
 *
 * @param totalRefunded everything refunded for the order so far (zero after the only refund failed)
 * @param full whether the whole payment has been refunded
 */
public record PaymentRefunded(UUID orderId, Money totalRefunded, boolean full) {

    /** Whether anything has been refunded at all. */
    public boolean any() {
        return totalRefunded.amount() > 0;
    }
}
