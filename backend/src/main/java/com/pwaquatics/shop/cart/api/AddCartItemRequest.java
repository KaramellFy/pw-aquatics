package com.pwaquatics.shop.cart.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Only an id and a quantity — prices are never accepted from a client. */
public record AddCartItemRequest(@NotNull UUID variantId, @NotNull @Min(1) @Max(20) Integer quantity) {}
