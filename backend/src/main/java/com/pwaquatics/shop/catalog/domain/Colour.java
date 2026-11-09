package com.pwaquatics.shop.catalog.domain;

import com.pwaquatics.shop.common.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "colours")
public class Colour extends AbstractEntity {

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "hex_code", length = 7)
    private String hexCode;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Colour() {}

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getHexCode() {
        return hexCode;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
