package com.pwaquatics.shop.coupon.api.admin;

import static com.pwaquatics.shop.common.text.Texts.trimToNull;

import com.pwaquatics.shop.audit.application.Actor;
import com.pwaquatics.shop.common.error.BadRequestException;
import com.pwaquatics.shop.coupon.application.AdminCouponService;
import com.pwaquatics.shop.coupon.application.AdminCouponService.CouponInput;
import com.pwaquatics.shop.coupon.application.view.AdminCouponView;
import com.pwaquatics.shop.coupon.domain.CouponStatus;
import com.pwaquatics.shop.pricing.domain.DiscountType;
import com.pwaquatics.shop.security.ShopUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Coupon management (docs/API.md §3.6). Role ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/coupons")
@Tag(name = "Admin: coupons")
class AdminCouponController {

    private final AdminCouponService coupons;

    AdminCouponController(AdminCouponService coupons) {
        this.coupons = coupons;
    }

    /**
     * @param code only on create: 3–32 letters, digits, hyphens or underscores (stored in upper case)
     * @param value basis points for {@code PERCENTAGE} (1000 = 10 %, at most 10 000), cents for
     *     {@code FIXED_AMOUNT}, 0 for {@code FREE_SHIPPING}
     * @param minOrderCents goods value (gross, before the discount) an order must reach; 0 or left out for none
     * @param maxDiscountCents cap of a percentage discount, or null
     * @param startsOn first day it works (shop time), or null for "right away"
     * @param endsOn last day it works (shop time, included), or null for "no end"
     * @param version the version you loaded (required to change a coupon, ignored on create)
     */
    record CouponRequest(
            @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{2,31}") String code,
            @Size(max = 255) String description,
            @NotNull DiscountType type,
            @PositiveOrZero @Max(10_000_000) long value,
            @PositiveOrZero @Max(10_000_000) Long minOrderCents,
            @Positive @Max(10_000_000) Long maxDiscountCents,
            LocalDate startsOn,
            LocalDate endsOn,
            @Min(1) @Max(1_000_000) Integer usageLimitTotal,
            @Min(1) @Max(1_000) Integer usageLimitPerCustomer,
            boolean active,
            Long version) {

        CouponInput input() {
            return new CouponInput(trimToNull(description), type, value, minOrderCents == null ? 0 : minOrderCents,
                    maxDiscountCents, startsOn, endsOn, usageLimitTotal, usageLimitPerCustomer, active);
        }
    }

    @Operation(summary = "Coupons, newest first, optionally filtered by text and current status")
    @GetMapping
    List<AdminCouponView> list(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) CouponStatus status) {
        return coupons.list(q, status);
    }

    @Operation(summary = "One coupon")
    @GetMapping("/{id}")
    AdminCouponView get(@PathVariable UUID id) {
        return coupons.get(id);
    }

    @Operation(summary = "Create a coupon")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AdminCouponView create(
            @Valid @RequestBody CouponRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        if (request.code() == null) {
            throw new BadRequestException("VALIDATION_FAILED", "A new coupon needs a code.");
        }
        return coupons.create(request.code(), request.input(), actor(principal, http));
    }

    @Operation(summary = "Change a coupon (the code stays; send the version you loaded)")
    @PutMapping("/{id}")
    AdminCouponView update(
            @PathVariable UUID id,
            @Valid @RequestBody CouponRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        if (request.version() == null) {
            throw new BadRequestException("VALIDATION_FAILED", "Send the version of the coupon you loaded.");
        }
        return coupons.update(id, request.input(), request.version(), actor(principal, http));
    }

    @Operation(summary = "Delete a coupon that no order has used")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, @AuthenticationPrincipal ShopUserPrincipal principal, HttpServletRequest http) {
        coupons.delete(id, actor(principal, http));
    }

    private static Actor actor(ShopUserPrincipal principal, HttpServletRequest http) {
        return new Actor(principal.userId(), http.getRemoteAddr());
    }
}
