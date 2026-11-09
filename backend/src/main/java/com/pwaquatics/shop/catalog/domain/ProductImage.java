package com.pwaquatics.shop.catalog.domain;

import com.pwaquatics.shop.common.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A product photo; sized WebP renditions are derived from {@code storageKey}. */
@Entity
@Table(name = "product_images")
public class ProductImage extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "alt_text", nullable = false)
    private String altText;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ProductImage() {}

    public String getStorageKey() {
        return storageKey;
    }

    public String getAltText() {
        return altText;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
