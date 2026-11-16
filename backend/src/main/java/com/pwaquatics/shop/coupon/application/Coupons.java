package com.pwaquatics.shop.coupon.application;

import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.coupon.application.view.CouponStateView;
import com.pwaquatics.shop.coupon.application.view.CouponTerms;
import com.pwaquatics.shop.coupon.domain.Coupon;
import com.pwaquatics.shop.coupon.domain.CouponCode;
import com.pwaquatics.shop.coupon.domain.CouponProblem;
import com.pwaquatics.shop.coupon.domain.CouponRedemption;
import com.pwaquatics.shop.coupon.infrastructure.CouponRedemptionRepository;
import com.pwaquatics.shop.coupon.infrastructure.CouponRepository;
import com.pwaquatics.shop.pricing.domain.Discount;
import com.pwaquatics.shop.pricing.domain.DiscountOutcome;
import com.pwaquatics.shop.pricing.domain.DiscountType;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coupons for the cart and the checkout: finding a code, checking whether it may be used,
 * and recording (or giving back) a redemption with the order.
 *
 * <p>Whether a coupon reduces a particular cart (minimum order value, reduced products) and
 * by how much is decided by {@code pricing.domain.PriceCalculator}; {@link #state} turns that
 * result into what the customer sees.
 */
@Service
public class Coupons {

    private final CouponRepository coupons;
    private final CouponRedemptionRepository redemptions;
    private final Clock clock;

    Coupons(CouponRepository coupons, CouponRedemptionRepository redemptions, Clock clock) {
        this.coupons = coupons;
        this.redemptions = redemptions;
        this.clock = clock;
    }

    /**
     * A code a customer typed in.
     *
     * @param userId the signed-in customer (for the per-customer limit), or null for a guest
     * @throws BusinessRuleException {@code COUPON_INVALID} for an unknown code
     */
    @Transactional(readOnly = true)
    public CouponTerms byCode(String code, UUID userId) {
        String normalised = CouponCode.normalise(code);
        return (CouponCode.isValid(normalised) ? coupons.findByCode(normalised) : Optional.<Coupon>empty())
                .map(coupon -> terms(coupon, userId))
                .orElseThrow(() -> problem(CouponProblem.INVALID));
    }

    /** The coupon attached to a cart, or empty if it has been deleted meanwhile. */
    @Transactional(readOnly = true)
    public Optional<CouponTerms> byId(UUID couponId, UUID userId) {
        return coupons.findById(couponId).map(coupon -> terms(coupon, userId));
    }

    /**
     * Like {@link #byId}, but locks the coupon until the order transaction ends, so its usage
     * counter cannot change between this check and {@link #redeem}. Called after the products
     * are locked (lock order: cart → products → coupon).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<CouponTerms> lockForOrder(UUID couponId, UUID userId) {
        return coupons.lockById(couponId).map(coupon -> terms(coupon, userId));
    }

    /**
     * Records that an order used the coupon (same transaction as the order).
     *
     * @param saving what the customer saved: the discount, or the waived shipping price
     * @throws BusinessRuleException {@code COUPON_USAGE_LIMIT_REACHED} when the coupon was used up
     *     or switched off meanwhile (the order is rolled back)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void redeem(UUID couponId, UUID orderId, UUID userId, Money saving) {
        if (coupons.countRedemption(couponId) == 0) {
            throw problem(CouponProblem.USAGE_LIMIT_REACHED);
        }
        redemptions.save(new CouponRedemption(couponId, orderId, userId, saving.amount(), clock.instant()));
    }

    /** Gives the coupon of a cancelled order back, so it can be used again. Does nothing without one. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(UUID orderId) {
        redemptions.findByOrderId(orderId).ifPresent(redemption -> {
            if (redemption.release(clock.instant())) {
                coupons.releaseRedemption(redemption.getCouponId());
            }
        });
    }

    /**
     * What the customer sees of the coupon in a cart or quote.
     *
     * @param outcome what the price calculator made of the coupon's terms
     * @param saving the discount, or the waived shipping price
     */
    public static CouponStateView state(CouponTerms terms, DiscountOutcome outcome, Money saving) {
        CouponProblem problem = terms.problem() != null ? terms.problem() : switch (outcome) {
            case MIN_ORDER_NOT_MET -> CouponProblem.MIN_ORDER_NOT_MET;
            case NOTHING_ELIGIBLE -> CouponProblem.NOT_APPLICABLE;
            case APPLIED, NONE -> null;
        };
        Discount discount = terms.discount();
        return new CouponStateView(
                terms.code(),
                terms.description(),
                discount.type(),
                discount.type() == DiscountType.PERCENTAGE ? Math.toIntExact(discount.value()) : null,
                discount.type() == DiscountType.FIXED_AMOUNT ? Money.eur(discount.value()) : null,
                discount.maxDiscountCents() == null ? null : Money.eur(discount.maxDiscountCents()),
                discount.minOrderCents() == 0 ? null : Money.eur(discount.minOrderCents()),
                problem == null,
                problem == null ? null : problem.code(),
                problem == null ? saving : Money.eur(0));
    }

    /** The error a customer gets for a coupon that cannot be used. */
    public static BusinessRuleException problem(CouponProblem problem) {
        return new BusinessRuleException(problem.code(), problem.message());
    }

    private CouponTerms terms(Coupon coupon, UUID userId) {
        Long used = userId == null ? null : redemptions.countByCouponIdAndUserIdAndReleasedAtIsNull(coupon.getId(), userId);
        return new CouponTerms(
                coupon.getId(),
                coupon.getCode(),
                coupon.getDescription(),
                coupon.discount(),
                coupon.problemAt(clock.instant(), used).orElse(null));
    }
}
