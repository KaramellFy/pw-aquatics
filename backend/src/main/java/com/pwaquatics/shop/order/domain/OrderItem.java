package com.pwaquatics.shop.order.domain;

import com.pwaquatics.shop.common.persistence.AbstractEntity;
import com.pwaquatics.shop.pricing.domain.PriceCalculator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One purchased line. Name, price and tax rate are a snapshot from the moment of purchase;
 * the links to product and variant only serve stock and statistics and may disappear.
 */
@Entity
@Table(name = "order_items")
public class OrderItem extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "variant_id")
    private UUID variantId;

    @Column(nullable = false, updatable = false)
    private int position;

    @Column(nullable = false, length = 64, updatable = false)
    private String sku;

    @Column(name = "product_name", nullable = false, length = 150, updatable = false)
    private String productName;

    @Column(name = "variant_name", nullable = false, length = 100, updatable = false)
    private String variantName;

    @Column(name = "species_name", length = 150, updatable = false)
    private String speciesName;

    @Column(name = "grade_name", length = 60, updatable = false)
    private String gradeName;

    @Column(name = "is_live_animal", nullable = false, updatable = false)
    private boolean liveAnimal;

    @Column(name = "pack_size", nullable = false, updatable = false)
    private int packSize;

    @Column(name = "unit_price_cents", nullable = false, updatable = false)
    private long unitPriceCents;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "discount_cents", nullable = false, updatable = false)
    private long discountCents;

    @Column(name = "line_total_cents", nullable = false, updatable = false)
    private long lineTotalCents;

    @Column(name = "tax_rate_bps", nullable = false, updatable = false)
    private int taxRateBps;

    @Column(name = "tax_cents", nullable = false, updatable = false)
    private long taxCents;

    protected OrderItem() {}

    OrderItem(Order order, int position, NewOrderItem item) {
        if (item.quantity() < 1 || item.packSize() < 1 || item.unitPriceCents() < 0) {
            throw new IllegalArgumentException("Invalid order item");
        }
        this.order = order;
        this.position = position;
        this.productId = item.productId();
        this.variantId = item.variantId();
        this.sku = item.sku();
        this.productName = item.productName();
        this.variantName = item.variantName();
        this.speciesName = item.speciesName();
        this.gradeName = item.gradeName();
        this.liveAnimal = item.liveAnimal();
        this.packSize = item.packSize();
        this.unitPriceCents = item.unitPriceCents();
        this.quantity = item.quantity();
        this.discountCents = 0;
        this.lineTotalCents = Math.multiplyExact(item.unitPriceCents(), (long) item.quantity());
        this.taxRateBps = item.taxRateBps();
        this.taxCents = PriceCalculator.includedTaxCents(lineTotalCents, item.taxRateBps());
    }

    /** Animals this line took out of stock. */
    public int stockUnits() {
        return Math.multiplyExact(packSize, quantity);
    }

    public UUID getProductId() {
        return productId;
    }

    public UUID getVariantId() {
        return variantId;
    }

    public int getPosition() {
        return position;
    }

    public String getSku() {
        return sku;
    }

    public String getProductName() {
        return productName;
    }

    public String getVariantName() {
        return variantName;
    }

    public String getSpeciesName() {
        return speciesName;
    }

    public String getGradeName() {
        return gradeName;
    }

    public boolean isLiveAnimal() {
        return liveAnimal;
    }

    public int getPackSize() {
        return packSize;
    }

    public long getUnitPriceCents() {
        return unitPriceCents;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getLineTotalCents() {
        return lineTotalCents;
    }

    public int getTaxRateBps() {
        return taxRateBps;
    }
}
