package com.pwaquatics.shop.catalog.application;

import com.pwaquatics.shop.catalog.application.view.CategoryRef;
import com.pwaquatics.shop.catalog.application.view.ColourView;
import com.pwaquatics.shop.catalog.application.view.ProductDetailView;
import com.pwaquatics.shop.catalog.application.view.ProductSummaryView;
import com.pwaquatics.shop.catalog.application.view.SeoView;
import com.pwaquatics.shop.catalog.application.view.VariantView;
import com.pwaquatics.shop.catalog.domain.Category;
import com.pwaquatics.shop.catalog.domain.Colour;
import com.pwaquatics.shop.catalog.domain.Product;
import com.pwaquatics.shop.catalog.domain.ProductImage;
import com.pwaquatics.shop.catalog.domain.ProductVariant;
import com.pwaquatics.shop.common.text.Markdown;
import com.pwaquatics.shop.media.application.ImageView;
import com.pwaquatics.shop.media.application.MediaUrls;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Builds product views. All values that involve rules (prices, availability) come from the domain. */
@Component
class ProductViewAssembler {

    /** Upper bound for {@link VariantView#maxQuantity()}, so exact stock levels stay private. */
    static final int MAX_QUANTITY_PER_LINE = 20;

    private final CatalogViewMapper mapper;
    private final MediaUrls mediaUrls;

    ProductViewAssembler(CatalogViewMapper mapper, MediaUrls mediaUrls) {
        this.mapper = mapper;
        this.mediaUrls = mediaUrls;
    }

    ProductSummaryView summary(Product product) {
        List<ImageView> images = images(product);
        ProductVariant cheapest = product.cheapestVariant().orElse(null);
        return new ProductSummaryView(
                product.getId(),
                product.getSlug(),
                product.getName(),
                product.getSpecies() == null ? null : mapper.toRef(product.getSpecies()),
                mapper.toRef(product.getCategory()),
                product.getGrade() == null ? null : mapper.toRef(product.getGrade()),
                colours(product),
                cheapest == null ? null : cheapest.price(),
                cheapest == null ? null : cheapest.compareAtPrice(),
                product.activeVariants().size() > 1,
                product.availability(),
                product.isLiveAnimal(),
                images.isEmpty() ? null : images.getFirst(),
                images.size() < 2 ? null : images.get(1));
    }

    ProductDetailView detail(Product product) {
        List<VariantView> variants = product.activeVariants().stream()
                .map(variant -> new VariantView(
                        variant.getId(),
                        variant.getSku(),
                        variant.getName(),
                        variant.getPackSize(),
                        variant.price(),
                        variant.compareAtPrice(),
                        product.isAvailable(variant),
                        product.maxQuantity(variant, MAX_QUANTITY_PER_LINE)))
                .toList();
        return new ProductDetailView(
                product.getId(),
                product.getSlug(),
                product.getName(),
                product.getShortDescription(),
                Markdown.toSafeHtml(product.getDescription()),
                Markdown.toSafeHtml(product.getCareNotes()),
                product.getSpecies() == null ? null : mapper.toView(product.getSpecies()),
                categoryPath(product.getCategory()),
                product.getGrade() == null ? null : mapper.toRef(product.getGrade()),
                colours(product),
                product.priceFrom().orElse(null),
                product.availability(),
                product.getAvailableFrom(),
                product.isLiveAnimal(),
                variants,
                images(product),
                new SeoView(
                        product.getSeoTitle() != null ? product.getSeoTitle() : product.getName(),
                        product.getSeoDescription() != null
                                ? product.getSeoDescription()
                                : product.getShortDescription()));
    }

    private List<ColourView> colours(Product product) {
        return product.getColours().stream()
                .sorted(Comparator.comparingInt(Colour::getSortOrder).thenComparing(Colour::getName))
                .map(mapper::toView)
                .toList();
    }

    private List<ImageView> images(Product product) {
        return product.getImages().stream()
                .sorted(Comparator.comparingInt(ProductImage::getSortOrder))
                .map(image -> mediaUrls.image(image.getStorageKey(), image.getAltText(), image.getWidth(),
                        image.getHeight()))
                .toList();
    }

    /** Root-first path for breadcrumbs, e.g. Shrimp › Neocaridina. */
    private List<CategoryRef> categoryPath(Category category) {
        List<CategoryRef> path = new ArrayList<>();
        for (Category current = category; current != null; current = current.getParent()) {
            path.add(mapper.toRef(current));
        }
        Collections.reverse(path);
        return path;
    }
}
