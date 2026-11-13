package com.pwaquatics.shop.payment.domain;

import com.pwaquatics.shop.common.money.Money;
import java.util.UUID;

/**
 * Money of a paid order went back to the customer (refunds are made in the provider's
 * dashboard; the provider tells us through a webhook).
 *
 * @param totalRefunded everything refunded for the order so far
 * @param full whether the whole payment has been refunded
 */
public record PaymentRefunded(UUID orderId, Money totalRefunded, boolean full) {}
