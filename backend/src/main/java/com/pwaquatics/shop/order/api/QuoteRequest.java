package com.pwaquatics.shop.order.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record QuoteRequest(@NotNull UUID shippingAddressId, @NotBlank @Size(max = 50) String shippingMethodCode) {}
