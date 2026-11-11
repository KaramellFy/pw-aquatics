package com.pwaquatics.shop.catalog.domain;

import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.common.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A species with its care parameters, e.g. Neocaridina davidi. */
@Entity
@Table(name = "species")
public class Species extends AbstractEntity {

    @Column(name = "scientific_name", nullable = false, unique = true, length = 150)
    private String scientificName;

    @Column(name = "common_name", length = 150)
    private String commonName;

    @Column(nullable = false, unique = true, length = 170)
    private String slug;

    private String description;

    @Column(length = 150)
    private String origin;

    @Column(name = "temp_min_c", precision = 4, scale = 1)
    private BigDecimal tempMinC;

    @Column(name = "temp_max_c", precision = 4, scale = 1)
    private BigDecimal tempMaxC;

    @Column(name = "ph_min", precision = 3, scale = 1)
    private BigDecimal phMin;

    @Column(name = "ph_max", precision = 3, scale = 1)
    private BigDecimal phMax;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "gh_min")
    private Integer ghMin;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "gh_max")
    private Integer ghMax;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "kh_min")
    private Integer khMin;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "kh_max")
    private Integer khMax;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "tds_min")
    private Integer tdsMin;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "tds_max")
    private Integer tdsMax;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "adult_size_mm")
    private Integer adultSizeMm;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "lifespan_months")
    private Integer lifespanMonths;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Difficulty difficulty;

    /** True for species that may not be traded (e.g. EU invasive alien species). */
    @Column(name = "trade_restricted", nullable = false)
    private boolean tradeRestricted;

    @Column(name = "restriction_note")
    private String restrictionNote;

    protected Species() {}

    /** Changes all editable fields. Ranges must not be inverted (minimum above maximum). */
    public void update(SpeciesData data) {
        requireRange(data.tempMinC(), data.tempMaxC(), "temperature");
        requireRange(data.phMin(), data.phMax(), "pH");
        requireRange(data.ghMin(), data.ghMax(), "GH");
        requireRange(data.khMin(), data.khMax(), "KH");
        requireRange(data.tdsMin(), data.tdsMax(), "TDS");
        scientificName = data.scientificName();
        commonName = data.commonName();
        slug = data.slug();
        description = data.description();
        origin = data.origin();
        tempMinC = data.tempMinC();
        tempMaxC = data.tempMaxC();
        phMin = data.phMin();
        phMax = data.phMax();
        ghMin = data.ghMin();
        ghMax = data.ghMax();
        khMin = data.khMin();
        khMax = data.khMax();
        tdsMin = data.tdsMin();
        tdsMax = data.tdsMax();
        adultSizeMm = data.adultSizeMm();
        lifespanMonths = data.lifespanMonths();
        difficulty = data.difficulty();
        tradeRestricted = data.tradeRestricted();
        restrictionNote = data.tradeRestricted() ? data.restrictionNote() : null;
    }

    private static <T extends Comparable<T>> void requireRange(T min, T max, String what) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new BusinessRuleException("RANGE_INVALID", "The minimum " + what + " is above the maximum.");
        }
    }

    public Species(String scientificName, String commonName, String slug) {
        this.scientificName = scientificName;
        this.commonName = commonName;
        this.slug = slug;
    }

    /** Marks the species as not tradeable (e.g. listed as invasive). */
    public void restrictTrade(String note) {
        this.tradeRestricted = true;
        this.restrictionNote = note;
    }

    public String getScientificName() {
        return scientificName;
    }

    public String getCommonName() {
        return commonName;
    }

    public String getSlug() {
        return slug;
    }

    public String getDescription() {
        return description;
    }

    public String getOrigin() {
        return origin;
    }

    public BigDecimal getTempMinC() {
        return tempMinC;
    }

    public BigDecimal getTempMaxC() {
        return tempMaxC;
    }

    public BigDecimal getPhMin() {
        return phMin;
    }

    public BigDecimal getPhMax() {
        return phMax;
    }

    public Integer getGhMin() {
        return ghMin;
    }

    public Integer getGhMax() {
        return ghMax;
    }

    public Integer getKhMin() {
        return khMin;
    }

    public Integer getKhMax() {
        return khMax;
    }

    public Integer getTdsMin() {
        return tdsMin;
    }

    public Integer getTdsMax() {
        return tdsMax;
    }

    public Integer getAdultSizeMm() {
        return adultSizeMm;
    }

    public Integer getLifespanMonths() {
        return lifespanMonths;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public boolean isTradeRestricted() {
        return tradeRestricted;
    }

    public String getRestrictionNote() {
        return restrictionNote;
    }
}
