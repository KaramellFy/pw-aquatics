package com.pwaquatics.shop.payment.infrastructure;

import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.common.error.ServiceUnavailableException;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.payment.application.StripeGateway;
import com.stripe.StripeClient;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link StripeGateway} on Stripe's official Java SDK. Only ids, amounts and the order number
 * go to Stripe from here; the customer enters card details in Stripe's own form.
 */
@Component
class StripeApiClient implements StripeGateway {

    private static final Logger log = LoggerFactory.getLogger(StripeApiClient.class);

    private final StripeClient client;

    StripeApiClient(AppProperties properties) {
        String secretKey = properties.stripe().secretKey();
        this.client = secretKey == null || secretKey.isBlank() ? null : new StripeClient(secretKey);
    }

    @Override
    public PaymentIntentData createPaymentIntent(UUID orderId, String orderNumber, Money amount) {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amount.amount())
                .setCurrency(amount.currency().toLowerCase(Locale.ROOT))
                // Cards; Apple Pay and Google Pay are card wallets and appear in the same form.
                .addAllowedPaymentMethodType(PaymentIntentCreateParams.AllowedPaymentMethodType.CARD)
                .setDescription("Order " + orderNumber)
                .putMetadata("order_number", orderNumber)
                .putMetadata("order_id", orderId.toString())
                .build();
        // The same key for the same order: a repeated call returns the first PaymentIntent.
        RequestOptions options = RequestOptions.builder().setIdempotencyKey("order-" + orderId + "-intent").build();
        return call(() -> data(stripe().v1().paymentIntents().create(params, options)));
    }

    @Override
    public PaymentIntentData retrievePaymentIntent(String paymentIntentId) {
        return call(() -> data(stripe().v1().paymentIntents().retrieve(paymentIntentId)));
    }

    @Override
    public PaymentIntentData cancelPaymentIntent(String paymentIntentId) {
        return call(() -> {
            try {
                return data(stripe().v1().paymentIntents().cancel(paymentIntentId));
            } catch (InvalidRequestException e) {
                // Already succeeded, processing or cancelled: report how it stands instead.
                if ("payment_intent_unexpected_state".equals(e.getCode())) {
                    return data(stripe().v1().paymentIntents().retrieve(paymentIntentId));
                }
                throw e;
            }
        });
    }

    private StripeClient stripe() {
        if (client == null) {
            throw new IllegalStateException("Stripe is not configured");
        }
        return client;
    }

    private static PaymentIntentData data(PaymentIntent intent) {
        return new PaymentIntentData(
                intent.getId(),
                intent.getStatus(),
                intent.getClientSecret(),
                new Money(intent.getAmount(), intent.getCurrency().toUpperCase(Locale.ROOT)));
    }

    private interface StripeCall {
        PaymentIntentData run() throws StripeException;
    }

    private static PaymentIntentData call(StripeCall call) {
        try {
            return call.run();
        } catch (StripeException e) {
            // Stripe's error code and request id help with support; no customer data is involved.
            log.warn("Stripe request failed: {} {} (request {})", e.getClass().getSimpleName(), e.getCode(),
                    e.getRequestId());
            throw new ServiceUnavailableException(
                    "PAYMENT_PROVIDER_UNAVAILABLE", "The payment service is not available right now. Please try again.", e);
        }
    }
}
