package com.pwaquatics.shop.payment.domain;

import com.pwaquatics.shop.common.money.Money;
import java.util.UUID;

/**
 * An online payment for an order has been confirmed by the provider (through a verified
 * webhook). Handed to the order module inside the transaction that records it, which marks
 * the order as paid.
 */
public record PaymentSucceeded(UUID orderId, PaymentMethod provider, Money amount) {}
