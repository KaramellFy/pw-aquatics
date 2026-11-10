package com.pwaquatics.shop.order.api;

import com.pwaquatics.shop.payment.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Body of {@code POST /checkout/orders}. There are deliberately no items and no prices in
 * here: both come from the server. {@code expectedTotal} is only compared with the server's
 * total so the customer never pays an amount they have not seen.
 *
 * @param billingAddressId omitted or null: bill to the shipping address
 */
public record PlaceOrderRequest(
        @NotNull UUID shippingAddressId,
        UUID billingAddressId,
        @NotBlank @Size(max = 50) String shippingMethodCode,
        @NotNull PaymentMethod paymentMethod,
        @Size(max = 1000) String customerNote,
        @AssertTrue(message = "The terms must be accepted") boolean acceptedTerms,
        boolean acceptedLiveAnimalConditions,
        @NotNull @Valid ExpectedTotal expectedTotal) {

    /** The total the customer saw on the review page. */
    public record ExpectedTotal(@Min(0) long amount, @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {}
}
