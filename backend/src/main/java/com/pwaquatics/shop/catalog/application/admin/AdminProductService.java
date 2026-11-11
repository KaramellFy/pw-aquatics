package com.pwaquatics.shop.catalog.application.admin;

import com.pwaquatics.shop.audit.application.Actor;
import com.pwaquatics.shop.audit.application.AuditService;
import com.pwaquatics.shop.catalog.application.admin.AdminProductQuery.StockFilter;
import com.pwaquatics.shop.catalog.application.admin.view.AdminImageView;
import com.pwaquatics.shop.catalog.application.admin.view.AdminProductSummaryView;
import com.pwaquatics.shop.catalog.application.admin.view.AdminProductView;
import com.pwaquatics.shop.catalog.application.admin.view.AdminVariantView;
import com.pwaquatics.shop.catalog.application.admin.view.MarkdownPreviewView;
import com.pwaquatics.shop.catalog.domain.Category;
import com.pwaquatics.shop.catalog.domain.Colour;
import com.pwaquatics.shop.catalog.domain.Grade;
import com.pwaquatics.shop.catalog.domain.Product;
import com.pwaquatics.shop.catalog.domain.ProductDetails;
import com.pwaquatics.shop.catalog.domain.ProductImage;
import com.pwaquatics.shop.catalog.domain.ProductStatus;
import com.pwaquatics.shop.catalog.domain.ProductVariant;
import com.pwaquatics.shop.catalog.domain.Species;
import com.pwaquatics.shop.catalog.infrastructure.CategoryRepository;
import com.pwaquatics.shop.catalog.infrastructure.ColourRepository;
import com.pwaquatics.shop.catalog.infrastructure.GradeRepository;
import com.pwaquatics.shop.catalog.infrastructure.ProductRepository;
import com.pwaquatics.shop.catalog.infrastructure.ProductVariantRepository;
import com.pwaquatics.shop.catalog.infrastructure.SpeciesRepository;
import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.common.error.ConflictException;
import com.pwaquatics.shop.common.error.NotFoundException;
import com.pwaquatics.shop.common.text.Markdown;
import com.pwaquatics.shop.media.application.ImageService;
import com.pwaquatics.shop.media.application.ImageView;
import com.pwaquatics.shop.media.application.MediaUrls;
import com.pwaquatics.shop.media.application.StoredImage;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Products, pack sizes and photos for the admin area (docs/API.md §3.2). Stock is changed
 * only through the inventory (§3.4), never here. Every change is recorded in the audit log.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductService {

    private static final int SLUG_MAX = 180;
    private static final int SKU_MAX = 64;
    private static final int NAME_MAX = 150;

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final CategoryRepository categories;
    private final SpeciesRepository species;
    private final GradeRepository grades;
    private final ColourRepository colours;
    private final ImageService images;
    private final MediaUrls mediaUrls;
    private final AuditService audit;
    private final Clock clock;

    AdminProductService(
            ProductRepository products,
            ProductVariantRepository variants,
            CategoryRepository categories,
            SpeciesRepository species,
            GradeRepository grades,
            ColourRepository colours,
            ImageService images,
            MediaUrls mediaUrls,
            AuditService audit,
            Clock clock) {
        this.products = products;
        this.variants = variants;
        this.categories = categories;
        this.species = species;
        this.grades = grades;
        this.colours = colours;
        this.images = images;
        this.mediaUrls = mediaUrls;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public Page<AdminProductSummaryView> list(AdminProductQuery query) {
        Set<ProductStatus> statuses = query.status() == null
                ? EnumSet.allOf(ProductStatus.class)
                : EnumSet.of(query.status());
        String text = query.q() == null ? "" : query.q().strip().toLowerCase(Locale.ROOT);
        String pattern = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        StockFilter stock = query.stock() == null ? StockFilter.ALL : query.stock();
        Sort sort = switch (query.sort() == null ? AdminProductQuery.AdminProductSort.UPDATED_DESC : query.sort()) {
            case UPDATED_DESC -> Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("name"));
            case NAME_ASC -> Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));
            case STOCK_ASC -> Sort.by(Sort.Order.asc("stockUnits"), Sort.Order.asc("name"));
        };
        return products.searchForAdmin(
                        statuses,
                        query.categoryId() == null,
                        query.categoryId() == null ? UUID.randomUUID() : query.categoryId(),
                        stock == StockFilter.LOW_STOCK,
                        stock == StockFilter.OUT_OF_STOCK,
                        pattern,
                        PageRequest.of(query.page(), query.size(), sort))
                .map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public AdminProductView get(UUID id) {
        return toView(load(id));
    }

    /** Markdown rendered exactly as the shop will show it (for the editor's preview). */
    public MarkdownPreviewView previewMarkdown(String markdown) {
        return new MarkdownPreviewView(Markdown.toSafeHtml(markdown));
    }

    // ------------------------------------------------------------------ product

    /** Creates an unpublished product without stock; pack sizes and photos are added next. */
    @Transactional
    public AdminProductView create(ProductInput input, Actor actor) {
        ProductDetails details = withSlug(input.details(),
                AdminSlugs.resolve(input.details().slug(), input.details().name(), SLUG_MAX, products::existsBySlug));
        Product product = Product.draft(details, category(input.categoryId()), species(input.speciesId()),
                grade(input.gradeId()), colours(input.colourIds()), clock.instant());
        products.save(product);
        audit.record(actor, "PRODUCT_CREATED", "PRODUCT", product.getId(), Map.of("slug", product.getSlug()));
        return toView(product);
    }

    /**
     * Saves the editor's changes.
     *
     * @param version the version the editor loaded
     * @throws ConflictException {@code CONFLICT} if someone else saved the product in between
     */
    @Transactional
    public AdminProductView update(UUID id, ProductInput input, long version, Actor actor) {
        Product product = load(id);
        if (product.getVersion() != version) {
            throw new ConflictException(
                    "CONFLICT", "This product was changed in the meantime. Please reload it and try again.");
        }
        ProductDetails details = withSlug(input.details(), AdminSlugs.resolve(input.details().slug(),
                input.details().name(), SLUG_MAX, slug -> products.existsBySlugAndIdNot(slug, id)));
        product.updateDetails(details, category(input.categoryId()), species(input.speciesId()),
                grade(input.gradeId()), colours(input.colourIds()), clock.instant());
        products.flush();
        audit.record(actor, "PRODUCT_UPDATED", "PRODUCT", id, Map.of("slug", product.getSlug()));
        return toView(product);
    }

    /**
     * Puts the product in the shop.
     *
     * @throws BusinessRuleException {@code SPECIES_RESTRICTED}, {@code NO_ACTIVE_VARIANT} or {@code NO_IMAGE}
     */
    @Transactional
    public AdminProductView publish(UUID id, Actor actor) {
        Product product = load(id);
        product.activate(clock.instant());
        products.flush();
        audit.record(actor, "PRODUCT_PUBLISHED", "PRODUCT", id, Map.of("slug", product.getSlug()));
        return toView(product);
    }

    /** Takes the product out of the shop; carts show it as unavailable, orders keep it. */
    @Transactional
    public AdminProductView archive(UUID id, Actor actor) {
        Product product = load(id);
        product.archive(clock.instant());
        products.flush();
        audit.record(actor, "PRODUCT_ARCHIVED", "PRODUCT", id, Map.of("slug", product.getSlug()));
        return toView(product);
    }

    /**
     * An unpublished copy with the same texts, taxonomy and pack sizes, but no stock and no
     * photos. Web address and SKUs get a free "-copy" suffix.
     */
    @Transactional
    public AdminProductView duplicate(UUID id, Actor actor) {
        Product original = load(id);
        String slug = AdminSlugs.firstFree(original.getSlug() + "-copy", SLUG_MAX, products::existsBySlug);
        String name = truncate(original.getName() + " (copy)", NAME_MAX);
        Set<String> usedSkus = new HashSet<>();
        Product copy = original.copyAsDraft(slug, name, sku -> {
            String free = firstFreeSku(sku + "-COPY", usedSkus);
            usedSkus.add(free);
            return free;
        }, clock.instant());
        products.save(copy);
        audit.record(actor, "PRODUCT_DUPLICATED", "PRODUCT", copy.getId(),
                Map.of("slug", copy.getSlug(), "copiedFrom", original.getId().toString()));
        return toView(copy);
    }

    // ------------------------------------------------------------------ pack sizes

    @Transactional
    public AdminProductView addVariant(UUID productId, VariantInput input, Actor actor) {
        Product product = load(productId);
        String sku = normaliseSku(input.sku());
        if (variants.existsBySkuIgnoreCase(sku)) {
            throw skuTaken();
        }
        ProductVariant variant = product.addVariant(sku, input.name(), input.packSize(), input.priceCents(),
                input.compareAtPriceCents());
        if (!input.active()) {
            product.updateVariant(variant, sku, input.name(), input.packSize(), input.priceCents(),
                    input.compareAtPriceCents(), false, clock.instant());
        }
        products.flush();
        audit.record(actor, "VARIANT_CREATED", "PRODUCT", productId,
                Map.of("sku", sku, "priceCents", input.priceCents()));
        return toView(product);
    }

    /**
     * Changes a pack size. The new price applies to carts immediately; orders keep the price
     * they were placed with.
     */
    @Transactional
    public AdminProductView updateVariant(UUID productId, UUID variantId, VariantInput input, Actor actor) {
        Product product = load(productId);
        ProductVariant variant = product.getVariants().stream()
                .filter(candidate -> candidate.getId().equals(variantId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Pack size not found."));
        String sku = normaliseSku(input.sku());
        if (variants.existsBySkuIgnoreCaseAndIdNot(sku, variantId)) {
            throw skuTaken();
        }
        long previousPrice = variant.getPriceCents();
        product.updateVariant(variant, sku, input.name(), input.packSize(), input.priceCents(),
                input.compareAtPriceCents(), input.active(), clock.instant());
        products.flush();
        audit.record(actor, "VARIANT_UPDATED", "PRODUCT", productId, Map.of(
                "sku", sku, "priceCents", input.priceCents(), "previousPriceCents", previousPrice,
                "active", input.active()));
        return toView(product);
    }

    // ------------------------------------------------------------------ photos

    /**
     * Adds a photo: it is checked, turned upright, stripped of all metadata and stored as WebP
     * in several sizes (see {@link ImageService}).
     *
     * @param altText describes the photo for screen readers and search engines
     */
    @Transactional
    public AdminProductView uploadImage(UUID productId, byte[] file, String altText, Actor actor) {
        Product product = load(productId);
        if (product.getImages().size() >= Product.MAX_IMAGES) {
            throw new BusinessRuleException(
                    "IMAGE_LIMIT_REACHED", "A product can have up to " + Product.MAX_IMAGES + " photos.");
        }
        StoredImage stored = images.storeProductImage(productId, file);
        ProductImage image = product.addImage(stored.storageKey(), altText, stored.width(), stored.height(),
                clock.instant());
        products.flush();
        audit.record(actor, "IMAGE_UPLOADED", "PRODUCT", productId, Map.of("imageId", image.getId().toString()));
        return toView(product);
    }

    @Transactional
    public AdminProductView changeAltText(UUID productId, UUID imageId, String altText, Actor actor) {
        Product product = load(productId);
        ownImage(product, imageId).changeAltText(altText);
        products.flush();
        audit.record(actor, "IMAGE_UPDATED", "PRODUCT", productId, Map.of("imageId", imageId.toString()));
        return toView(product);
    }

    /** Puts the photos in this order; the first one becomes the main photo. */
    @Transactional
    public AdminProductView reorderImages(UUID productId, List<UUID> imageIds, Actor actor) {
        Product product = load(productId);
        product.reorderImages(imageIds, clock.instant());
        products.flush();
        audit.record(actor, "IMAGES_REORDERED", "PRODUCT", productId, Map.of());
        return toView(product);
    }

    /** Removes a photo; its files are deleted once the change is saved. */
    @Transactional
    public AdminProductView deleteImage(UUID productId, UUID imageId, Actor actor) {
        Product product = load(productId);
        ProductImage image = ownImage(product, imageId);
        product.removeImage(image, clock.instant());
        images.deleteAfterCommit(image.getStorageKey());
        products.flush();
        audit.record(actor, "IMAGE_DELETED", "PRODUCT", productId, Map.of("imageId", imageId.toString()));
        return toView(product);
    }

    // ------------------------------------------------------------------ helpers

    private Product load(UUID id) {
        return products.findForAdmin(id).orElseThrow(() -> new NotFoundException("Product not found."));
    }

    private Category category(UUID id) {
        return categories.findById(id).orElseThrow(() -> new NotFoundException("Category not found."));
    }

    private Species species(UUID id) {
        return id == null ? null : species.findById(id).orElseThrow(() -> new NotFoundException("Species not found."));
    }

    private Grade grade(UUID id) {
        return id == null ? null : grades.findById(id).orElseThrow(() -> new NotFoundException("Grade not found."));
    }

    private Set<Colour> colours(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        Set<UUID> wanted = new LinkedHashSet<>(ids);
        List<Colour> found = colours.findAllById(wanted);
        if (found.size() != wanted.size()) {
            throw new NotFoundException("Colour not found.");
        }
        return new LinkedHashSet<>(found);
    }

    private static ProductImage ownImage(Product product, UUID imageId) {
        return product.getImages().stream()
                .filter(image -> image.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Photo not found."));
    }

    private static ProductDetails withSlug(ProductDetails d, String slug) {
        return new ProductDetails(d.name(), slug, d.shortDescription(), d.description(), d.careNotes(),
                d.liveAnimal(), d.featured(), d.lowStockThreshold(), d.availableFrom(), d.taxRateBps(),
                d.seoTitle(), d.seoDescription());
    }

    private static String normaliseSku(String sku) {
        return sku.strip().toUpperCase(Locale.ROOT);
    }

    private String firstFreeSku(String base, Set<String> reserved) {
        String stem = truncate(base, SKU_MAX);
        String candidate = stem;
        for (int n = 2; reserved.contains(candidate) || variants.existsBySkuIgnoreCase(candidate); n++) {
            String suffix = "-" + n;
            candidate = truncate(base, SKU_MAX - suffix.length()) + suffix;
        }
        return candidate;
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static ConflictException skuTaken() {
        return new ConflictException("SKU_TAKEN", "Another pack size already uses this SKU.");
    }

    private AdminProductSummaryView toSummary(Product p) {
        ImageView image = p.getImages().stream()
                .min(Comparator.comparingInt(ProductImage::getSortOrder))
                .map(i -> mediaUrls.image(i.getStorageKey(), i.getAltText(), i.getWidth(), i.getHeight()))
                .orElse(null);
        return new AdminProductSummaryView(
                p.getId(),
                p.getSlug(),
                p.getName(),
                p.getStatus(),
                p.getCategory().getName(),
                p.getSpecies() == null ? null : p.getSpecies().getScientificName(),
                p.getGrade() == null ? null : p.getGrade().getName(),
                p.getStockUnits(),
                p.getLowStockThreshold(),
                p.availability(),
                p.priceFrom().orElse(null),
                p.getVariants().size(),
                image,
                p.getUpdatedAt());
    }

    private AdminProductView toView(Product p) {
        return new AdminProductView(
                p.getId(),
                p.getVersion(),
                p.getSlug(),
                p.getName(),
                p.getStatus(),
                p.getCategory().getId(),
                p.getSpecies() == null ? null : p.getSpecies().getId(),
                p.getGrade() == null ? null : p.getGrade().getId(),
                p.getColours().stream().map(Colour::getId).toList(),
                p.getShortDescription(),
                p.getDescription(),
                p.getCareNotes(),
                p.isLiveAnimal(),
                p.isFeatured(),
                p.getStockUnits(),
                p.getLowStockThreshold(),
                p.getAvailableFrom(),
                p.getTaxRateBps(),
                p.getSeoTitle(),
                p.getSeoDescription(),
                p.availability(),
                p.getPublishedAt(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                p.getVariants().stream()
                        .sorted(Comparator.comparingInt(ProductVariant::getSortOrder)
                                .thenComparingInt(ProductVariant::getPackSize))
                        .map(v -> new AdminVariantView(v.getId(), v.getSku(), v.getName(), v.getPackSize(), v.price(),
                                v.compareAtPrice(), v.isActive(), v.getSortOrder()))
                        .toList(),
                p.getImages().stream()
                        .sorted(Comparator.comparingInt(ProductImage::getSortOrder))
                        .map(this::toImageView)
                        .toList(),
                p.publishProblems());
    }

    private AdminImageView toImageView(ProductImage image) {
        ImageView view = mediaUrls.image(image.getStorageKey(), image.getAltText(), image.getWidth(), image.getHeight());
        return new AdminImageView(image.getId(), view.url(), view.srcSet(), image.getAltText(), image.getWidth(),
                image.getHeight(), image.getSortOrder());
    }
}
