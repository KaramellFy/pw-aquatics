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
import com.pwaquatics.shop.common.text.ContentLanguage;
import com.pwaquatics.shop.common.text.Markdown;
import com.pwaquatics.shop.media.application.ImageView;
import com.pwaquatics.shop.media.application.MediaUrls;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds product views. All values that involve rules (prices, availability) come from the domain.
 * Texts are in the request's language ({@link ContentLanguage#current()}), falling back to English.
 */
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
        ContentLanguage lang = ContentLanguage.current();
        List<ImageView> images = images(product);
        ProductVariant cheapest = product.cheapestVariant().orElse(null);
        return new ProductSummaryView(
                product.getId(),
                product.getSlug(),
                lang.pick(product.getName(), product.getNameDe()),
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
        ContentLanguage lang = ContentLanguage.current();
        List<VariantView> variants = product.activeVariants().stream()
                .map(variant -> new VariantView(
                        variant.getId(),
                        variant.getSku(),
                        lang.pick(variant.getName(), variant.getNameDe()),
                        variant.getPackSize(),
                        variant.price(),
                        variant.compareAtPrice(),
                        product.isAvailable(variant),
                        product.maxQuantity(variant, MAX_QUANTITY_PER_LINE)))
                .toList();
        return new ProductDetailView(
                product.getId(),
                product.getSlug(),
                lang.pick(product.getName(), product.getNameDe()),
                lang.pick(product.getShortDescription(), product.getShortDescriptionDe()),
                Markdown.toSafeHtml(lang.pick(product.getDescription(), product.getDescriptionDe())),
                Markdown.toSafeHtml(lang.pick(product.getCareNotes(), product.getCareNotesDe())),
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
                seo(product, lang),
                Translations.of(product.getDescriptionDe(), product.getShortDescriptionDe()));
    }

    /**
     * Title and description for search engines: the SEO text in the language, else the name or
     * short text in the language, else the English ones.
     */
    private static SeoView seo(Product product, ContentLanguage lang) {
        String title = firstWritten(
                lang == ContentLanguage.DE ? product.getSeoTitleDe() : null,
                lang == ContentLanguage.DE ? product.getNameDe() : null,
                product.getSeoTitle(),
                product.getName());
        String description = firstWritten(
                lang == ContentLanguage.DE ? product.getSeoDescriptionDe() : null,
                lang == ContentLanguage.DE ? product.getShortDescriptionDe() : null,
                product.getSeoDescription(),
                product.getShortDescription());
        return new SeoView(title, description);
    }

    private static String firstWritten(String... texts) {
        for (String text : texts) {
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
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
                .map(image -> mediaUrls.image(image.getStorageKey(),
                        ContentLanguage.current().pick(image.getAltText(), image.getAltTextDe()), image.getWidth(),
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
