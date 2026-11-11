package com.pwaquatics.shop.order.application.view;

import com.pwaquatics.shop.payment.domain.PaymentMethod;
import com.pwaquatics.shop.shipping.application.view.ShippingOption;
import java.util.List;

/**
 * The binding price breakdown for the current cart, a destination and a shipping method
 * (docs/API.md §2.5). The client sends {@code totals.total} back as {@code expectedTotal}
 * when placing the order.
 *
 * @param containsLiveAnimals the customer must accept the live-animal conditions
 * @param paymentMethods payment methods that can be chosen right now
 */
public record QuoteView(
        List<QuoteLineView> items,
        OrderTotalsView totals,
        ShippingOption shippingMethod,
        boolean containsLiveAnimals,
        List<PaymentMethod> paymentMethods) {}
