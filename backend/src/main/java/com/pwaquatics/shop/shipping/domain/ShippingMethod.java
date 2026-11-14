package com.pwaquatics.shop.shipping.domain;

import com.pwaquatics.shop.common.persistence.AbstractEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A way to ship an order: price, the countries it delivers to, and the weekdays on which
 * parcels are handed to the carrier. Live animals only travel with methods that allow them
 * and only on those weekdays, so they never spend a weekend in a depot.
 */
@Entity
@Table(name = "shipping_methods")
public class ShippingMethod extends AbstractEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(length = 50)
    private String carrier;

    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Column(name = "free_shipping_threshold_cents")
    private Long freeShippingThresholdCents;

    @Column(name = "live_animals_allowed", nullable = false)
    private boolean liveAnimalsAllowed = true;

    /** ISO weekday numbers (1 = Monday ... 7 = Sunday). */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "dispatch_weekdays", nullable = false, columnDefinition = "smallint[]")
    private short[] dispatchWeekdays = {1, 2, 3};

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "estimated_days_min")
    private Integer estimatedDaysMin;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "estimated_days_max")
    private Integer estimatedDaysMax;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "shipping_method_countries", joinColumns = @JoinColumn(name = "shipping_method_id"))
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", nullable = false, length = 2)
    private Set<String> countries = new HashSet<>();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected ShippingMethod() {}

    /** A new method; {@code code} identifies it in orders and cannot be changed later. */
    public static ShippingMethod create(String code, ShippingMethodData data, Instant now) {
        ShippingMethod method = new ShippingMethod();
        method.code = code;
        method.apply(data, now);
        return method;
    }

    /** Changes everything but the code. Orders keep the name and price they were placed with. */
    public void update(ShippingMethodData data, Instant now) {
        apply(data, now);
    }

    private void apply(ShippingMethodData data, Instant now) {
        name = data.name();
        description = data.description();
        carrier = data.carrier();
        priceCents = data.priceCents();
        freeShippingThresholdCents = data.freeShippingThresholdCents();
        liveAnimalsAllowed = data.liveAnimalsAllowed();
        dispatchWeekdays = new short[data.dispatchDays().size()];
        int i = 0;
        for (DayOfWeek day : new TreeSet<>(data.dispatchDays())) {
            dispatchWeekdays[i++] = (short) day.getValue();
        }
        estimatedDaysMin = data.estimatedDaysMin();
        estimatedDaysMax = data.estimatedDaysMax();
        active = data.active();
        sortOrder = data.sortOrder();
        countries.clear();
        countries.addAll(data.countries());
        updatedAt = now;
    }

    // ---------------------------------------------------------------- rules

    /** Can this method carry the given kind of parcel to the given country? */
    public boolean serves(String countryCode, boolean containsLiveAnimals) {
        return active && countries.contains(countryCode) && (liveAnimalsAllowed || !containsLiveAnimals);
    }

    /** The gross shipping price for goods worth {@code subtotalCents}: free from the threshold on. */
    public long priceFor(long subtotalCents) {
        boolean free = freeShippingThresholdCents != null && subtotalCents >= freeShippingThresholdCents;
        return free ? 0 : priceCents;
    }

    public Set<DayOfWeek> dispatchDays() {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (short weekday : dispatchWeekdays) {
            days.add(DayOfWeek.of(weekday));
        }
        return days;
    }

    // ---------------------------------------------------------------- accessors

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCarrier() {
        return carrier;
    }

    public Long getFreeShippingThresholdCents() {
        return freeShippingThresholdCents;
    }

    public Integer getEstimatedDaysMin() {
        return estimatedDaysMin;
    }

    public Integer getEstimatedDaysMax() {
        return estimatedDaysMax;
    }

    public long getPriceCents() {
        return priceCents;
    }

    public boolean isLiveAnimalsAllowed() {
        return liveAnimalsAllowed;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    /** Destination countries, alphabetically. */
    public Set<String> getCountries() {
        return new TreeSet<>(countries);
    }

    public long getVersion() {
        return version;
    }
}
