package com.pwaquatics.shop.settings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One shop-wide setting: a key and a small JSON value (docs/DATABASE.md §3.4). */
@Entity
@Table(name = "shop_settings")
public class ShopSetting {

    @Id
    @Column(length = 100)
    private String key;

    /** Raw JSON text. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected ShopSetting() {}

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
