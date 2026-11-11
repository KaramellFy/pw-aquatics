package com.pwaquatics.shop.catalog.domain;

import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.common.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A pack size of a product with its own price, e.g. "10 animals". Consumes {@code packSize} stock units. */
@Entity
@Table(name = "product_variants")
public class ProductVariant extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "pack_size", nullable = false)
    private int packSize;

    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Column(name = "compare_at_price_cents")
    private Long compareAtPriceCents;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Version
    private long version;

    protected ProductVariant() {}

    /**
     * Changes the pack size. Prices are set by the shop owner in the admin area; they are
     * never taken from a customer's request.
     */
    void update(String sku, String name, int packSize, long priceCents, Long compareAtPriceCents, boolean active) {
        requireValid(packSize, priceCents, compareAtPriceCents);
        this.sku = sku;
        this.name = name;
        this.packSize = packSize;
        this.priceCents = priceCents;
        this.compareAtPriceCents = compareAtPriceCents;
        this.active = active;
    }

    void moveTo(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    static void requireValid(int packSize, long priceCents, Long compareAtPriceCents) {
        if (packSize < 1 || priceCents < 0) {
            throw new BusinessRuleException("VARIANT_INVALID", "Pack size and price must be positive.");
        }
        if (compareAtPriceCents != null && compareAtPriceCents <= priceCents) {
            throw new BusinessRuleException(
                    "COMPARE_AT_PRICE_INVALID", "The previous price must be higher than the price.");
        }
    }

    ProductVariant(
            Product product, String sku, String name, int packSize, long priceCents, Long compareAtPriceCents,
            int sortOrder) {
        this.product = product;
        this.sku = sku;
        this.name = name;
        this.packSize = packSize;
        this.priceCents = priceCents;
        this.compareAtPriceCents = compareAtPriceCents;
        this.sortOrder = sortOrder;
    }

    public Money price() {
        return Money.eur(priceCents);
    }

    public Money compareAtPrice() {
        return compareAtPriceCents == null ? null : Money.eur(compareAtPriceCents);
    }

    public Product getProduct() {
        return product;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public int getPackSize() {
        return packSize;
    }

    public long getPriceCents() {
        return priceCents;
    }

    public Long getCompareAtPriceCents() {
        return compareAtPriceCents;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
