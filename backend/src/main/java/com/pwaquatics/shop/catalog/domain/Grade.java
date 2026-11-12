package com.pwaquatics.shop.catalog.domain;

import com.pwaquatics.shop.common.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A quality grade, e.g. "High" (Neocaridina) or "SSS" (Crystal Red). Higher rank = better. */
@Entity
@Table(name = "grades")
public class Grade extends AbstractEntity {

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(length = 50)
    private String scale;

    private String description;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private int rank;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Grade() {}

    public Grade(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    /** @param rank higher means better; used to sort products by grade */
    public void update(String name, String slug, String scale, String description, int rank, int sortOrder) {
        this.name = name;
        this.slug = slug;
        this.scale = scale;
        this.description = description;
        this.rank = rank;
        this.sortOrder = sortOrder;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getScale() {
        return scale;
    }

    public String getDescription() {
        return description;
    }

    public int getRank() {
        return rank;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
