package com.pwaquatics.shop.shipping.application;

import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.common.error.ConflictException;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.settings.application.ShopSettings;
import com.pwaquatics.shop.shipping.application.view.ShippingOption;
import com.pwaquatics.shop.shipping.application.view.ShippingOptions;
import com.pwaquatics.shop.shipping.domain.DispatchCalendar;
import com.pwaquatics.shop.shipping.domain.ShippingMethod;
import com.pwaquatics.shop.shipping.domain.ShippingPause;
import com.pwaquatics.shop.shipping.infrastructure.ShippingMethodRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shipping methods, prices and dispatch dates for a cart. Calendar rules use the shop's
 * time zone (Europe/Berlin).
 */
@Service
@Transactional(readOnly = true)
public class ShippingService {

    /** Calendar rules (dispatch days, payment deadlines) are evaluated in this zone. */
    public static final ZoneId SHOP_ZONE = ZoneId.of("Europe/Berlin");

    private final ShippingMethodRepository methods;
    private final ShopSettings settings;
    private final int handlingDays;
    private final Clock clock;

    ShippingService(ShippingMethodRepository methods, ShopSettings settings, AppProperties properties, Clock clock) {
        this.methods = methods;
        this.settings = settings;
        this.handlingDays = properties.shipping().handlingDays();
        this.clock = clock;
    }

    /**
     * The shipping methods for a destination and cart, with prices and dispatch dates.
     *
     * @param countryCode ISO 3166-1 alpha-2 code of the destination
     * @param containsLiveAnimals whether the parcel contains live animals
     * @param subtotalCents gross goods value (decides about free shipping)
     */
    public ShippingOptions optionsFor(String countryCode, boolean containsLiveAnimals, long subtotalCents) {
        LocalDate today = LocalDate.now(clock.withZone(SHOP_ZONE));
        ShippingPause pause = containsLiveAnimals ? currentPause(today) : ShippingPause.NONE;
        if (pause.isOpenEnded()) {
            return new ShippingOptions(List.of(), true, pause.message(), null);
        }
        List<ShippingOption> options = methods.findActiveFor(countryCode).stream()
                .filter(method -> method.serves(countryCode, containsLiveAnimals))
                .map(method -> option(method, subtotalCents, today, pause.until()))
                .toList();
        return new ShippingOptions(options, pause.active(), pause.message(), pause.until());
    }

    /**
     * The chosen method for an order, validated against destination and cart.
     *
     * @throws ConflictException {@code SHIPPING_PAUSED} or {@code SHIPPING_METHOD_UNAVAILABLE}
     */
    public ShippingOption require(String code, String countryCode, boolean containsLiveAnimals, long subtotalCents) {
        ShippingOptions options = optionsFor(countryCode, containsLiveAnimals, subtotalCents);
        if (options.paused() && options.methods().isEmpty()) {
            throw new ConflictException(
                    "SHIPPING_PAUSED", "Shipping of live animals is paused at the moment. Please try again later.");
        }
        return options.methods().stream()
                .filter(option -> option.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new ConflictException(
                        "SHIPPING_METHOD_UNAVAILABLE", "This shipping method is not available for your order."));
    }

    private ShippingPause currentPause(LocalDate today) {
        return settings.find(ShippingPause.SETTING_KEY, ShippingPause.class)
                .filter(pause -> pause.isInForce(today))
                .orElse(ShippingPause.NONE);
    }

    private ShippingOption option(ShippingMethod method, long subtotalCents, LocalDate today, LocalDate pausedUntil) {
        LocalDate dispatch =
                DispatchCalendar.plannedDispatchDate(today, handlingDays, method.dispatchDays(), pausedUntil);
        Long threshold = method.getFreeShippingThresholdCents();
        return new ShippingOption(
                method.getCode(),
                method.getName(),
                method.getDescription(),
                method.getCarrier(),
                Money.eur(method.priceFor(subtotalCents)),
                threshold == null ? null : Money.eur(threshold),
                dispatch,
                method.getEstimatedDaysMin() == null ? null : dispatch.plusDays(method.getEstimatedDaysMin()),
                method.getEstimatedDaysMax() == null ? null : dispatch.plusDays(method.getEstimatedDaysMax()));
    }
}
