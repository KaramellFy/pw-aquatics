package com.pwaquatics.shop.order.application;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.payment.domain.PaymentMethod;
import java.util.UUID;

/**
 * A customer's request to buy the contents of their cart. It names addresses and methods by
 * id or code only; items, prices and the shipping price are taken from the server's data.
 *
 * @param billingAddressId null to bill to the shipping address
 * @param expectedTotal the total the customer saw; only compared, never used as a price
 */
public record PlaceOrder(
        UUID shippingAddressId,
        UUID billingAddressId,
        String shippingMethodCode,
        PaymentMethod paymentMethod,
        String customerNote,
        boolean acceptedLiveAnimalConditions,
        Money expectedTotal) {}
