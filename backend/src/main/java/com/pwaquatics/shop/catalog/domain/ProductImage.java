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

    /** German alt text, or null (the English one is used). */
    @Column(name = "alt_text_de")
    private String altTextDe;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ProductImage() {}

    ProductImage(Product product, String storageKey, String altText, int width, int height, int sortOrder) {
        this.product = product;
        this.storageKey = storageKey;
        this.altText = altText;
        this.width = width;
        this.height = height;
        this.sortOrder = sortOrder;
    }

    /** @param altTextDe the German alt text, or null */
    public void changeAltText(String altText, String altTextDe) {
        this.altText = altText;
        this.altTextDe = altTextDe;
    }

    void moveTo(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getAltText() {
        return altText;
    }

    public String getAltTextDe() {
        return altTextDe;
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
