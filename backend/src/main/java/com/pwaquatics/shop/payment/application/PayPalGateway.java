package com.pwaquatics.shop.payment.application;

import com.pwaquatics.shop.common.error.ServiceUnavailableException;
import com.pwaquatics.shop.common.money.Money;
import jakarta.annotation.Nullable;
import java.util.UUID;

/**
 * The calls we make to PayPal's REST API (Orders v2 and webhook verification). Implemented by
 * {@code PayPalApiClient}; tests replace it so they never reach the network. Every method throws
 * {@link ServiceUnavailableException} ({@code PAYMENT_PROVIDER_UNAVAILABLE}) when PayPal cannot
 * be reached or refuses the call for a reason other than the ones named here.
 */
public interface PayPalGateway {

    /**
     * A PayPal order as far as we need it.
     *
     * @param status PayPal's value: {@code CREATED}, {@code APPROVED} (the customer agreed, nothing
     *     was taken yet), {@code COMPLETED} (captured), {@code VOIDED}, {@code PAYER_ACTION_REQUIRED}
     * @param capture the capture once there is one
     */
    record PayPalOrder(String id, String status, Money amount, @Nullable Capture capture) {

        public boolean isVoided() {
            return "VOIDED".equals(status);
        }
    }

    /**
     * The money taken for a PayPal order.
     *
     * @param status {@code COMPLETED}, {@code PENDING} (PayPal or the bank still checks it),
     *     {@code DECLINED}, {@code FAILED}, {@code REFUNDED}, {@code PARTIALLY_REFUNDED}
     */
    record Capture(String id, String status, Money amount) {

        public boolean isCompleted() {
            return "COMPLETED".equals(status) || "REFUNDED".equals(status) || "PARTIALLY_REFUNDED".equals(status);
        }

        public boolean isPending() {
            return "PENDING".equals(status);
        }
    }

    /**
     * PayPal did not take the money: the customer's funding source was declined
     * ({@code INSTRUMENT_DECLINED}) or the customer has not approved the payment
     * ({@code ORDER_NOT_APPROVED}). The customer may approve again with another source.
     */
    final class CaptureRefused extends RuntimeException {

        private final String issue;

        public CaptureRefused(String issue) {
            super("PayPal refused the capture: " + issue);
            this.issue = issue;
        }

        public String issue() {
            return issue;
        }
    }

    /** The headers PayPal sends with a webhook delivery; PayPal checks them against the body. */
    record WebhookSignature(
            @Nullable String authAlgo,
            @Nullable String certUrl,
            @Nullable String transmissionId,
            @Nullable String transmissionSig,
            @Nullable String transmissionTime) {

        public boolean isComplete() {
            return present(authAlgo) && present(certUrl) && present(transmissionId) && present(transmissionSig)
                    && present(transmissionTime);
        }

        private static boolean present(String value) {
            return value != null && !value.isBlank();
        }
    }

    /**
     * Creates the PayPal order for a shop order. Repeating the call for the same shop order
     * returns the same PayPal order ({@code PayPal-Request-Id}).
     *
     * @param amount the stored order total — never an amount from a client
     * @param language "de" or "en", for PayPal's pages
     */
    PayPalOrder createOrder(UUID orderId, String orderNumber, Money amount, String language);

    PayPalOrder getOrder(String paypalOrderId);

    /**
     * Takes the money the customer approved. Repeating the call returns the same capture.
     *
     * @throws CaptureRefused if PayPal did not take the money and the customer may try again
     */
    PayPalOrder capture(String paypalOrderId, UUID orderId);

    /**
     * Asks PayPal whether a webhook delivery is genuine (signed by PayPal for our webhook).
     *
     * @param payload the request body exactly as received
     */
    boolean isGenuine(WebhookSignature signature, String payload);
}
