package com.pwaquatics.shop.catalog.domain;

import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.common.persistence.AbstractEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One sellable animal line, e.g. Neocaridina davidi "Blue Dream", grade High.
 *
 * <p>Stock is held here, counted in individual animals ({@code stockUnits}); each
 * {@link ProductVariant} is a pack size that consumes {@code packSize} units. This keeps
 * singles and groups from the same tank in one shared stock (docs/ARCHITECTURE.md §5.4).
 */
@Entity
@Table(name = "products")
public class Product extends AbstractEntity {

    @Column(nullable = false, unique = true, length = 180)
    private String slug;

    @Column(nullable = false, length = 150)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "species_id")
    private Species species;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id")
    private Grade grade;

    @Column(name = "short_description", length = 300)
    private String shortDescription;

    /** Markdown, rendered to sanitised HTML for the API. */
    private String description;

    @Column(name = "care_notes")
    private String careNotes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status = ProductStatus.DRAFT;

    @Column(name = "is_live_animal", nullable = false)
    private boolean liveAnimal = true;

    @Column(nullable = false)
    private boolean featured;

    @Column(name = "stock_units", nullable = false)
    private int stockUnits;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold = 5;

    @Column(name = "available_from")
    private LocalDate availableFrom;

    @Column(name = "tax_rate_bps", nullable = false)
    private int taxRateBps = 1900;

    @Column(name = "min_price_cents")
    private Long minPriceCents;

    @Column(name = "seo_title", length = 160)
    private String seoTitle;

    @Column(name = "seo_description", length = 320)
    private String seoDescription;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Version
    private long version;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "product_colours",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "colour_id"))
    private Set<Colour> colours = new LinkedHashSet<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, packSize ASC")
    private List<ProductVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ProductImage> images = new ArrayList<>();

    protected Product() {}

    public Product(String slug, String name, Category category, Species species, Grade grade) {
        this.slug = slug;
        this.name = name;
        this.category = category;
        this.species = species;
        this.grade = grade;
    }

    // ---------------------------------------------------------------- behaviour

    public ProductVariant addVariant(String sku, String variantName, int packSize, long priceCents, Long compareAtCents) {
        if (packSize < 1) {
            throw new IllegalArgumentException("packSize must be at least 1");
        }
        ProductVariant variant = new ProductVariant(this, sku, variantName, packSize, priceCents, compareAtCents,
                variants.size());
        variants.add(variant);
        recalculateMinPrice();
        return variant;
    }

    /** Changes stock by {@code delta} animals; stock can never become negative. */
    public void adjustStock(int delta) {
        if (stockUnits + delta < 0) {
            throw new BusinessRuleException("INSUFFICIENT_STOCK", "Stock cannot become negative.");
        }
        stockUnits += delta;
    }

    /** Makes the product visible in the shop. Refused for trade-restricted species. */
    public void activate(Instant now) {
        if (species != null && species.isTradeRestricted()) {
            throw new BusinessRuleException("SPECIES_RESTRICTED", "This species may not be traded.");
        }
        if (activeVariants().isEmpty()) {
            throw new BusinessRuleException("NO_ACTIVE_VARIANT", "A product needs at least one active variant.");
        }
        status = ProductStatus.ACTIVE;
        if (publishedAt == null) {
            publishedAt = now;
        }
    }

    public void setAvailableFrom(LocalDate availableFrom) {
        this.availableFrom = availableFrom;
    }

    public void setLowStockThreshold(int lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }

    // ---------------------------------------------------------------- rules

    /** Visible in the public shop: active and not a trade-restricted species. */
    public boolean isPubliclyVisible() {
        return status == ProductStatus.ACTIVE && (species == null || !species.isTradeRestricted());
    }

    public List<ProductVariant> activeVariants() {
        return variants.stream().filter(ProductVariant::isActive).toList();
    }

    /** A variant can be bought when it is active and enough animals are in stock for one pack. */
    public boolean isAvailable(ProductVariant variant) {
        return status == ProductStatus.ACTIVE && variant.isActive() && variant.getPackSize() <= stockUnits;
    }

    /** How many packs of {@code variant} can be bought right now, capped at {@code cap}. */
    public int maxQuantity(ProductVariant variant, int cap) {
        return isAvailable(variant) ? Math.min(cap, stockUnits / variant.getPackSize()) : 0;
    }

    public Availability availability() {
        boolean purchasable = variants.stream().anyMatch(this::isAvailable);
        if (purchasable) {
            return stockUnits <= lowStockThreshold ? Availability.LOW_STOCK : Availability.IN_STOCK;
        }
        return availableFrom != null ? Availability.COMING_SOON : Availability.OUT_OF_STOCK;
    }

    /** The cheapest active variant, used for "from €x" prices. */
    public Optional<ProductVariant> cheapestVariant() {
        return activeVariants().stream().min(Comparator.comparingLong(ProductVariant::getPriceCents));
    }

    public Optional<Money> priceFrom() {
        return cheapestVariant().map(ProductVariant::price);
    }

    private void recalculateMinPrice() {
        minPriceCents = cheapestVariant().map(ProductVariant::getPriceCents).orElse(null);
    }

    // ---------------------------------------------------------------- accessors

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public Species getSpecies() {
        return species;
    }

    public Category getCategory() {
        return category;
    }

    public Grade getGrade() {
        return grade;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public String getDescription() {
        return description;
    }

    public String getCareNotes() {
        return careNotes;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public boolean isLiveAnimal() {
        return liveAnimal;
    }

    public boolean isFeatured() {
        return featured;
    }

    public int getStockUnits() {
        return stockUnits;
    }

    public int getLowStockThreshold() {
        return lowStockThreshold;
    }

    public LocalDate getAvailableFrom() {
        return availableFrom;
    }

    public int getTaxRateBps() {
        return taxRateBps;
    }

    public Long getMinPriceCents() {
        return minPriceCents;
    }

    public String getSeoTitle() {
        return seoTitle;
    }

    public String getSeoDescription() {
        return seoDescription;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Set<Colour> getColours() {
        return colours;
    }

    public List<ProductVariant> getVariants() {
        return variants;
    }

    public List<ProductImage> getImages() {
        return images;
    }
}
