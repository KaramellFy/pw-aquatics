package com.pwaquatics.shop.cart.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A coupon code as the customer typed it (case and surrounding spaces do not matter). */
public record ApplyCouponRequest(@NotBlank @Size(max = 40) String code) {}
