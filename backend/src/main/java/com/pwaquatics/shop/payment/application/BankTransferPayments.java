package com.pwaquatics.shop.payment.application;

import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.payment.application.view.PaymentInstructionsView;
import com.pwaquatics.shop.payment.domain.PaymentMethod;
import org.springframework.stereotype.Service;

/**
 * Payment by bank transfer: the customer receives the shop's bank details and transfers the
 * order total with the order number as reference; the owner marks the order as paid when the
 * money has arrived. No payment data of the customer is processed or stored.
 */
@Service
public class BankTransferPayments {

    private final AppProperties.BankTransfer account;

    BankTransferPayments(AppProperties properties) {
        this.account = properties.bankTransfer();
    }

    /** Offered only when the shop's bank account is configured. */
    public boolean isAvailable() {
        return account.isConfigured();
    }

    /**
     * Transfer details for an order.
     *
     * @param orderNumber used as the payment reference
     * @param total the stored order total — never an amount sent by a client
     */
    public PaymentInstructionsView instructions(String orderNumber, Money total) {
        if (!isAvailable()) {
            throw new IllegalStateException("Bank transfer is not configured");
        }
        return new PaymentInstructionsView(
                PaymentMethod.BANK_TRANSFER,
                account.accountHolder(),
                account.iban(),
                blankToNull(account.bic()),
                blankToNull(account.bankName()),
                orderNumber,
                total);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
