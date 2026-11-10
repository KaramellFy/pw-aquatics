package com.pwaquatics.shop.payment.application.view;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.payment.domain.PaymentMethod;
import jakarta.annotation.Nullable;

/**
 * What the customer needs to pay an order by bank transfer.
 *
 * @param reference the payment reference to quote (the order number)
 * @param amount the amount to transfer (the stored order total)
 */
public record PaymentInstructionsView(
        PaymentMethod type,
        String accountHolder,
        String iban,
        @Nullable String bic,
        @Nullable String bankName,
        String reference,
        Money amount) {}
