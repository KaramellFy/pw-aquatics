package com.pwaquatics.shop.coupon.application.view;

import com.pwaquatics.shop.coupon.domain.CouponProblem;
import com.pwaquatics.shop.pricing.domain.Discount;
import java.util.UUID;

/**
 * A coupon as the cart and the checkout need it.
 *
 * @param discount the terms the price calculator applies
 * @param problem why it cannot be used right now regardless of the cart, or null
 */
public record CouponTerms(UUID id, String code, String description, Discount discount, CouponProblem problem) {}
