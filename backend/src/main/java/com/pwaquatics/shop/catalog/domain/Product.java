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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

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

    /**
     * Animals in stock. Never written through the entity: every change goes through
     * {@code CatalogStock}'s conditional SQL updates, so editing a product can never
     * overwrite stock that changed in the meantime (an order, a stock adjustment).
     */
    @Column(name = "stock_units", nullable = false, updatable = false)
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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    /** Most photos a product can have. */
    public static final int MAX_IMAGES = 20;

    public Product(String slug, String name, Category category, Species species, Grade grade) {
        this.slug = slug;
        this.name = name;
        this.category = category;
        this.species = species;
        this.grade = grade;
    }

    /** A new, unpublished product without stock, variants or photos. */
    public static Product draft(
            ProductDetails details, Category category, Species species, Grade grade, Set<Colour> colours, Instant now) {
        Product product = new Product(details.slug(), details.name(), category, species, grade);
        product.createdAt = now;
        product.updateDetails(details, category, species, grade, colours, now);
        return product;
    }

    // ---------------------------------------------------------------- behaviour

    /** Changes text, settings and taxonomy links. Stock, status, variants and photos are separate. */
    public void updateDetails(
            ProductDetails details, Category category, Species species, Grade grade, Set<Colour> colours, Instant now) {
        if (details.lowStockThreshold() < 0 || details.taxRateBps() < 0 || details.taxRateBps() > 10_000) {
            throw new BusinessRuleException("VALIDATION_FAILED", "Low-stock threshold or tax rate out of range.");
        }
        this.name = details.name();
        this.slug = details.slug();
        this.shortDescription = details.shortDescription();
        this.description = details.description();
        this.careNotes = details.careNotes();
        this.liveAnimal = details.liveAnimal();
        this.featured = details.featured();
        this.lowStockThreshold = details.lowStockThreshold();
        this.availableFrom = details.availableFrom();
        this.taxRateBps = details.taxRateBps();
        this.seoTitle = details.seoTitle();
        this.seoDescription = details.seoDescription();
        this.category = category;
        this.species = species;
        this.grade = grade;
        this.colours.clear();
        this.colours.addAll(colours);
        touch(now);
    }

    public ProductVariant addVariant(String sku, String variantName, int packSize, long priceCents, Long compareAtCents) {
        ProductVariant.requireValid(packSize, priceCents, compareAtCents);
        ProductVariant variant = new ProductVariant(this, sku, variantName, packSize, priceCents, compareAtCents,
                variants.size());
        variants.add(variant);
        recalculateMinPrice();
        return variant;
    }

    /** Changes a pack size of this product; deactivated pack sizes can no longer be bought. */
    public void updateVariant(
            ProductVariant variant, String sku, String variantName, int packSize, long priceCents,
            Long compareAtCents, boolean active, Instant now) {
        requireOwn(variants.contains(variant));
        variant.update(sku, variantName, packSize, priceCents, compareAtCents, active);
        recalculateMinPrice();
        touch(now);
    }

    /** Adds a photo at the end; the first photo is the main one. */
    public ProductImage addImage(String storageKey, String altText, int width, int height, Instant now) {
        if (images.size() >= MAX_IMAGES) {
            throw new BusinessRuleException("IMAGE_LIMIT_REACHED", "A product can have up to " + MAX_IMAGES + " photos.");
        }
        ProductImage image = new ProductImage(this, storageKey, altText, width, height, images.size());
        images.add(image);
        touch(now);
        return image;
    }

    /** Removes a photo. A published product keeps at least one. */
    public void removeImage(ProductImage image, Instant now) {
        requireOwn(images.contains(image));
        if (status == ProductStatus.ACTIVE && images.size() == 1) {
            throw new BusinessRuleException(
                    "LAST_IMAGE", "A published product needs a photo. Upload another one first.");
        }
        images.remove(image);
        renumberImages(images);
        touch(now);
    }

    /**
     * Puts the photos in the given order. The list must contain every photo of the product
     * exactly once.
     */
    public void reorderImages(List<UUID> imageIds, Instant now) {
        if (imageIds.size() != images.size() || new HashSet<>(imageIds).size() != imageIds.size()) {
            throw new BusinessRuleException("IMAGE_ORDER_INVALID", "The new order must list every photo once.");
        }
        List<ProductImage> ordered = new ArrayList<>();
        for (UUID id : imageIds) {
            ordered.add(images.stream()
                    .filter(image -> image.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException(
                            "IMAGE_ORDER_INVALID", "The new order must list every photo once.")));
        }
        renumberImages(ordered);
        images.sort(Comparator.comparingInt(ProductImage::getSortOrder));
        touch(now);
    }

    /**
     * What stands between this product and the shop, most fundamental first. Empty when it
     * can be published.
     */
    public List<PublishProblem> publishProblems() {
        List<PublishProblem> problems = new ArrayList<>();
        if (species != null && species.isTradeRestricted()) {
            problems.add(PublishProblem.SPECIES_RESTRICTED);
        }
        if (activeVariants().isEmpty()) {
            problems.add(PublishProblem.NO_ACTIVE_VARIANT);
        }
        if (images.isEmpty()) {
            problems.add(PublishProblem.NO_IMAGE);
        }
        return problems;
    }

    /**
     * Makes the product visible in the shop.
     *
     * @throws BusinessRuleException with the first {@link PublishProblem} as code
     */
    public void activate(Instant now) {
        List<PublishProblem> problems = publishProblems();
        if (!problems.isEmpty()) {
            PublishProblem problem = problems.getFirst();
            throw new BusinessRuleException(problem.name(), switch (problem) {
                case SPECIES_RESTRICTED -> "This species may not be traded.";
                case NO_ACTIVE_VARIANT -> "A product needs at least one active pack size.";
                case NO_IMAGE -> "A product needs at least one photo.";
            });
        }
        status = ProductStatus.ACTIVE;
        if (publishedAt == null) {
            publishedAt = now;
        }
        touch(now);
    }

    /** Takes the product out of the shop; orders and history keep it. */
    public void archive(Instant now) {
        status = ProductStatus.ARCHIVED;
        touch(now);
    }

    /**
     * An unpublished copy without stock and photos, e.g. for a new colour or grade of the same
     * animal. Photos are not shared, so deleting one product's photo never affects the other.
     *
     * @param skuFor the SKU each copied pack size gets
     */
    public Product copyAsDraft(String newSlug, String newName, UnaryOperator<String> skuFor,
            Instant now) {
        Product copy = draft(
                new ProductDetails(newName, newSlug, shortDescription, description, careNotes, liveAnimal, false,
                        lowStockThreshold, availableFrom, taxRateBps, seoTitle, seoDescription),
                category, species, grade, new LinkedHashSet<>(colours), now);
        for (ProductVariant variant : variants) {
            ProductVariant copied = copy.addVariant(skuFor.apply(variant.getSku()), variant.getName(),
                    variant.getPackSize(), variant.getPriceCents(), variant.getCompareAtPriceCents());
            if (!variant.isActive()) {
                copied.update(copied.getSku(), copied.getName(), copied.getPackSize(), copied.getPriceCents(),
                        copied.getCompareAtPriceCents(), false);
            }
        }
        copy.recalculateMinPrice();
        return copy;
    }

    /**
     * Sets the stock of an object that is not saved yet (tests, building objects). Saved
     * products change stock only through {@code CatalogStock}.
     */
    public void adjustStock(int delta) {
        if (stockUnits + delta < 0) {
            throw new BusinessRuleException("INSUFFICIENT_STOCK", "Stock cannot become negative.");
        }
        stockUnits += delta;
    }

    private void touch(Instant now) {
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    private static void renumberImages(List<ProductImage> ordered) {
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).moveTo(i);
        }
    }

    private static void requireOwn(boolean own) {
        if (!own) {
            throw new IllegalArgumentException("Not a part of this product");
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
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
