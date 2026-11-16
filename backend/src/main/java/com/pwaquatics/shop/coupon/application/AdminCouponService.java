package com.pwaquatics.shop.coupon.application;

import com.pwaquatics.shop.audit.application.Actor;
import com.pwaquatics.shop.audit.application.AuditService;
import com.pwaquatics.shop.common.error.BadRequestException;
import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.common.error.ConflictException;
import com.pwaquatics.shop.common.error.NotFoundException;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.coupon.application.view.AdminCouponView;
import com.pwaquatics.shop.coupon.domain.Coupon;
import com.pwaquatics.shop.coupon.domain.CouponCode;
import com.pwaquatics.shop.coupon.domain.CouponData;
import com.pwaquatics.shop.coupon.domain.CouponStatus;
import com.pwaquatics.shop.coupon.infrastructure.CouponRedemptionRepository;
import com.pwaquatics.shop.coupon.infrastructure.CouponRepository;
import com.pwaquatics.shop.pricing.domain.DiscountType;
import com.pwaquatics.shop.shipping.application.ShippingService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coupons for the shop owner (docs/API.md §3.6): create, change, switch off and delete.
 *
 * <p>Dates are whole days in shop time: a coupon works from the start of its first day to the
 * end of its last day. The code never changes once created; orders keep it as a snapshot.
 * A coupon that has been used is switched off rather than deleted.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminCouponService {

    private final CouponRepository coupons;
    private final CouponRedemptionRepository redemptions;
    private final AuditService audit;
    private final Clock clock;

    AdminCouponService(
            CouponRepository coupons, CouponRedemptionRepository redemptions, AuditService audit, Clock clock) {
        this.coupons = coupons;
        this.redemptions = redemptions;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * @param query part of the code or description, or null
     * @param status only coupons in this state right now, or null for all
     */
    @Transactional(readOnly = true)
    public List<AdminCouponView> list(String query, CouponStatus status) {
        Instant now = clock.instant();
        String needle = query == null || query.isBlank() ? null : query.strip().toLowerCase(Locale.ROOT);
        return coupons.findAllByOrderByCreatedAtDescIdDesc().stream()
                .filter(coupon -> needle == null || matches(coupon, needle))
                .filter(coupon -> status == null || coupon.statusAt(now) == status)
                .map(coupon -> view(coupon, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminCouponView get(UUID id) {
        return view(find(id), clock.instant());
    }

    /**
     * @param code as typed; stored in upper case
     * @throws BadRequestException {@code VALIDATION_FAILED} for a code outside the format
     * @throws ConflictException {@code CODE_TAKEN}
     */
    @Transactional
    public AdminCouponView create(String code, CouponInput input, Actor actor) {
        String normalised = CouponCode.normalise(code);
        if (!CouponCode.isValid(normalised)) {
            throw new BadRequestException("VALIDATION_FAILED",
                    "A code has 3 to 32 letters, digits, hyphens or underscores and starts with a letter or digit.");
        }
        if (coupons.existsByCode(normalised)) {
            throw new ConflictException("CODE_TAKEN", "Another coupon already uses this code.");
        }
        Instant now = clock.instant();
        Coupon coupon = coupons.save(Coupon.create(normalised, input.data(), actor.userId(), now));
        audit.record(actor, "COUPON_CREATED", "COUPON", coupon.getId(), details(coupon));
        return view(coupon, now);
    }

    /**
     * @param version the version the editor loaded
     * @throws ConflictException {@code CONFLICT} if someone else saved the coupon in between
     */
    @Transactional
    public AdminCouponView update(UUID id, CouponInput input, long version, Actor actor) {
        Coupon coupon = find(id);
        if (coupon.getVersion() != version) {
            throw new ConflictException(
                    "CONFLICT", "This coupon was changed in the meantime. Please reload it and try again.");
        }
        Instant now = clock.instant();
        coupon.update(input.data(), now);
        coupons.flush();
        audit.record(actor, "COUPON_UPDATED", "COUPON", id, details(coupon));
        return view(coupon, now);
    }

    /**
     * Deletes a coupon that has never been used (it is also taken out of carts).
     *
     * @throws ConflictException {@code COUPON_IN_USE} once an order has used it; switch it off instead
     */
    @Transactional
    public void delete(UUID id, Actor actor) {
        Coupon coupon = find(id);
        if (redemptions.existsByCouponId(id)) {
            throw new ConflictException(
                    "COUPON_IN_USE", "Orders have used this coupon, so it is kept. Switch it off instead.");
        }
        coupons.delete(coupon);
        audit.record(actor, "COUPON_DELETED", "COUPON", id, Map.of("code", coupon.getCode()));
    }

    /**
     * What the owner enters: dates as days in shop time (the last day is included).
     *
     * @param value basis points for a percentage (1000 = 10 %), cents for a fixed amount, 0 for free shipping
     */
    public record CouponInput(
            String description,
            DiscountType type,
            long value,
            long minOrderCents,
            Long maxDiscountCents,
            LocalDate startsOn,
            LocalDate endsOn,
            Integer usageLimitTotal,
            Integer usageLimitPerCustomer,
            boolean active) {

        CouponData data() {
            if (startsOn != null && endsOn != null && endsOn.isBefore(startsOn)) {
                throw new BusinessRuleException("RANGE_INVALID", "The last day must not be before the first day.");
            }
            return new CouponData(description, type, value, minOrderCents, maxDiscountCents,
                    startsOn == null ? null : startsOn.atStartOfDay(ShippingService.SHOP_ZONE).toInstant(),
                    endsOn == null ? null : endsOn.plusDays(1).atStartOfDay(ShippingService.SHOP_ZONE).toInstant(),
                    usageLimitTotal, usageLimitPerCustomer, active);
        }
    }

    private Coupon find(UUID id) {
        return coupons.findById(id).orElseThrow(() -> new NotFoundException("Coupon not found."));
    }

    private static boolean matches(Coupon coupon, String needle) {
        return coupon.getCode().toLowerCase(Locale.ROOT).contains(needle)
                || (coupon.getDescription() != null && coupon.getDescription().toLowerCase(Locale.ROOT).contains(needle));
    }

    private static Map<String, Object> details(Coupon coupon) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("code", coupon.getCode());
        details.put("type", coupon.getType().name());
        details.put("value", coupon.getValue());
        details.put("active", coupon.isActive());
        return details;
    }

    static AdminCouponView view(Coupon coupon, Instant now) {
        return new AdminCouponView(
                coupon.getId(),
                coupon.getCode(),
                coupon.getDescription(),
                coupon.getType(),
                coupon.getType() == DiscountType.PERCENTAGE ? Math.toIntExact(coupon.getValue()) : null,
                coupon.getType() == DiscountType.FIXED_AMOUNT ? Money.eur(coupon.getValue()) : null,
                coupon.getMaxDiscountCents() == null ? null : Money.eur(coupon.getMaxDiscountCents()),
                coupon.getMinOrderCents() == 0 ? null : Money.eur(coupon.getMinOrderCents()),
                coupon.getStartsAt() == null ? null : LocalDate.ofInstant(coupon.getStartsAt(), ShippingService.SHOP_ZONE),
                coupon.getEndsAt() == null
                        ? null
                        : LocalDate.ofInstant(coupon.getEndsAt(), ShippingService.SHOP_ZONE).minusDays(1),
                coupon.getUsageLimitTotal(),
                coupon.getUsageLimitPerCustomer(),
                coupon.getTimesRedeemed(),
                coupon.statusAt(now),
                coupon.isActive(),
                coupon.getCreatedAt(),
                coupon.getUpdatedAt(),
                coupon.getVersion());
    }
}
