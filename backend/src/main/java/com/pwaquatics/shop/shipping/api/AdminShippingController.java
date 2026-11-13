package com.pwaquatics.shop.shipping.api;

import static com.pwaquatics.shop.common.text.Texts.trimToNull;

import com.pwaquatics.shop.audit.application.Actor;
import com.pwaquatics.shop.common.error.BadRequestException;
import com.pwaquatics.shop.security.ShopUserPrincipal;
import com.pwaquatics.shop.shipping.application.AdminShippingService;
import com.pwaquatics.shop.shipping.application.view.AdminShippingMethodView;
import com.pwaquatics.shop.shipping.application.view.ShippingPauseView;
import com.pwaquatics.shop.shipping.domain.ShippingMethodData;
import com.pwaquatics.shop.shipping.domain.ShippingPause;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Shipping methods and the shipping pause (docs/API.md §3.7). Role ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin: shipping")
class AdminShippingController {

    private final AdminShippingService shipping;

    AdminShippingController(AdminShippingService shipping) {
        this.shipping = shipping;
    }

    /**
     * @param code only on create; letters, digits and underscores, e.g. {@code DHL_EXPRESS_LIVE}
     * @param priceCents gross price in cents
     * @param freeShippingThresholdCents goods value (gross, cents) from which shipping is free
     * @param dispatchDays weekdays on which parcels leave
     * @param countries ISO 3166-1 alpha-2 codes
     * @param version the version you loaded (ignored on create)
     */
    record ShippingMethodRequest(
            @Pattern(regexp = "[A-Z][A-Z0-9_]{1,49}") String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            @Size(max = 50) String carrier,
            @PositiveOrZero @Max(100_000) long priceCents,
            @Positive @Max(10_000_000) Long freeShippingThresholdCents,
            boolean liveAnimalsAllowed,
            @NotEmpty Set<@NotNull DayOfWeek> dispatchDays,
            @Min(0) @Max(60) Integer estimatedDaysMin,
            @Min(0) @Max(60) Integer estimatedDaysMax,
            @NotEmpty @Size(max = 60) Set<@NotNull @Pattern(regexp = "[A-Za-z]{2}") String> countries,
            boolean active,
            @Min(0) @Max(10_000) int sortOrder,
            long version) {

        ShippingMethodData data() {
            return new ShippingMethodData(name.strip(), trimToNull(description), trimToNull(carrier), priceCents,
                    freeShippingThresholdCents, liveAnimalsAllowed, dispatchDays, estimatedDaysMin, estimatedDaysMax,
                    countries.stream().map(c -> c.toUpperCase(Locale.ROOT)).collect(Collectors.toSet()), active,
                    sortOrder);
        }
    }

    /** @param until last day of the pause; leave out for an open-ended pause */
    record ShippingPauseRequest(boolean active, @Size(max = 300) String message, LocalDate until) {}

    @Operation(summary = "All shipping methods, active or not")
    @GetMapping("/shipping-methods")
    List<AdminShippingMethodView> list() {
        return shipping.list();
    }

    @Operation(summary = "Add a shipping method")
    @PostMapping("/shipping-methods")
    @ResponseStatus(HttpStatus.CREATED)
    AdminShippingMethodView create(
            @Valid @RequestBody ShippingMethodRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        if (request.code() == null) {
            throw new BadRequestException(
                    "VALIDATION_FAILED", "A new shipping method needs a code.");
        }
        return shipping.create(request.code(), request.data(), actor(principal, http));
    }

    @Operation(summary = "Change a shipping method (the code stays; send the version you loaded)")
    @PutMapping("/shipping-methods/{id}")
    AdminShippingMethodView update(
            @PathVariable UUID id,
            @Valid @RequestBody ShippingMethodRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return shipping.update(id, request.data(), request.version(), actor(principal, http));
    }

    @Operation(summary = "The shipping pause for live animals")
    @GetMapping("/settings/shipping-pause")
    ShippingPauseView pause() {
        return shipping.pause();
    }

    @Operation(summary = "Switch the shipping pause for live animals on or off")
    @PutMapping("/settings/shipping-pause")
    ShippingPauseView updatePause(
            @Valid @RequestBody ShippingPauseRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return shipping.updatePause(new ShippingPause(request.active(), trimToNull(request.message()),
                request.until()), actor(principal, http));
    }

    private static Actor actor(ShopUserPrincipal principal, HttpServletRequest http) {
        return new Actor(principal.userId(), http.getRemoteAddr());
    }
}
