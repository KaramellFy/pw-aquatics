package com.pwaquatics.shop.shipping.application;

import com.pwaquatics.shop.audit.application.Actor;
import com.pwaquatics.shop.audit.application.AuditService;
import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.common.error.ConflictException;
import com.pwaquatics.shop.common.error.NotFoundException;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.settings.application.ShopSettings;
import com.pwaquatics.shop.shipping.application.view.AdminShippingMethodView;
import com.pwaquatics.shop.shipping.application.view.ShippingPauseView;
import com.pwaquatics.shop.shipping.domain.ShippingMethod;
import com.pwaquatics.shop.shipping.domain.ShippingMethodData;
import com.pwaquatics.shop.shipping.domain.ShippingPause;
import com.pwaquatics.shop.shipping.infrastructure.ShippingMethodRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shipping settings for the shop owner (docs/API.md §3.7): shipping methods with prices,
 * countries and dispatch weekdays, and the shipping pause for live animals.
 *
 * <p>Changes apply to carts and new orders at once; placed orders keep the method name,
 * price and dispatch date they were placed with.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminShippingService {

    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

    private final ShippingMethodRepository methods;
    private final ShopSettings settings;
    private final AuditService audit;
    private final Clock clock;

    AdminShippingService(ShippingMethodRepository methods, ShopSettings settings, AuditService audit, Clock clock) {
        this.methods = methods;
        this.settings = settings;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AdminShippingMethodView> list() {
        return methods.findAllForAdmin().stream().map(AdminShippingService::view).toList();
    }

    /**
     * @param code permanent identifier, e.g. {@code DHL_EXPRESS_LIVE}
     * @throws ConflictException {@code CODE_TAKEN}
     * @throws BusinessRuleException {@code COUNTRY_INVALID}, {@code DELIVERY_ESTIMATE_INVALID}
     */
    @Transactional
    public AdminShippingMethodView create(String code, ShippingMethodData data, Actor actor) {
        if (methods.existsByCode(code)) {
            throw new ConflictException("CODE_TAKEN", "Another shipping method already uses this code.");
        }
        ShippingMethod method = methods.save(ShippingMethod.create(code, validated(data), clock.instant()));
        audit.record(actor, "SHIPPING_METHOD_CREATED", "SHIPPING_METHOD", method.getId(), Map.of("code", code));
        return view(method);
    }

    /**
     * @param version the version the editor loaded
     * @throws ConflictException {@code CONFLICT} if someone else saved the method in between
     */
    @Transactional
    public AdminShippingMethodView update(UUID id, ShippingMethodData data, long version, Actor actor) {
        ShippingMethod method = methods.findById(id)
                .orElseThrow(() -> new NotFoundException("Shipping method not found."));
        if (method.getVersion() != version) {
            throw new ConflictException(
                    "CONFLICT", "This shipping method was changed in the meantime. Please reload it and try again.");
        }
        method.update(validated(data), clock.instant());
        methods.flush();
        audit.record(actor, "SHIPPING_METHOD_UPDATED", "SHIPPING_METHOD", id, Map.of("code", method.getCode()));
        return view(method);
    }

    /** The shipping pause as stored (also when it has run out). */
    @Transactional(readOnly = true)
    public ShippingPauseView pause() {
        return pauseView(settings.find(ShippingPause.SETTING_KEY, ShippingPause.class).orElse(ShippingPause.NONE));
    }

    /**
     * Switches the shipping pause for live animals on or off. While it is on, no live animals
     * are dispatched up to and including {@code until}; without {@code until}, orders with live
     * animals cannot be placed at all.
     *
     * @throws BusinessRuleException {@code DATE_INVALID} if {@code until} has already passed
     */
    @Transactional
    public ShippingPauseView updatePause(ShippingPause pause, Actor actor) {
        if (pause.active() && pause.until() != null && pause.until().isBefore(today())) {
            throw new BusinessRuleException("DATE_INVALID", "The last day of the pause must not be in the past.");
        }
        ShippingPause stored = pause.active() ? pause : ShippingPause.NONE;
        settings.save(ShippingPause.SETTING_KEY, stored, actor.userId());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("active", stored.active());
        if (stored.until() != null) {
            details.put("until", stored.until().toString());
        }
        audit.record(actor, "SHIPPING_PAUSE_CHANGED", "SETTING", null, details);
        return pauseView(stored);
    }

    private ShippingMethodData validated(ShippingMethodData data) {
        for (String country : data.countries()) {
            if (!ISO_COUNTRIES.contains(country)) {
                throw new BusinessRuleException("COUNTRY_INVALID", "Unknown country: " + country + ".");
            }
        }
        if (data.estimatedDaysMin() != null && data.estimatedDaysMax() != null
                && data.estimatedDaysMax() < data.estimatedDaysMin()) {
            throw new BusinessRuleException(
                    "DELIVERY_ESTIMATE_INVALID", "The longest delivery time must not be shorter than the shortest.");
        }
        return data;
    }

    private ShippingPauseView pauseView(ShippingPause pause) {
        return new ShippingPauseView(pause.active(), pause.isInForce(today()), pause.message(), pause.until());
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ShippingService.SHOP_ZONE));
    }

    private static AdminShippingMethodView view(ShippingMethod method) {
        Long freeFrom = method.getFreeShippingThresholdCents();
        return new AdminShippingMethodView(
                method.getId(),
                method.getCode(),
                method.getName(),
                method.getDescription(),
                method.getCarrier(),
                Money.eur(method.getPriceCents()),
                freeFrom == null ? null : Money.eur(freeFrom),
                method.isLiveAnimalsAllowed(),
                method.dispatchDays().stream().sorted().toList(),
                method.getEstimatedDaysMin(),
                method.getEstimatedDaysMax(),
                List.copyOf(method.getCountries()),
                method.isActive(),
                method.getSortOrder(),
                method.getVersion());
    }
}
