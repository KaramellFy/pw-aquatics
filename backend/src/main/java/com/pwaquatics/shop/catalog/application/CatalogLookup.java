package com.pwaquatics.shop.catalog.application;

import com.pwaquatics.shop.catalog.application.view.VariantSnapshot;
import com.pwaquatics.shop.catalog.domain.Colour;
import com.pwaquatics.shop.catalog.domain.Product;
import com.pwaquatics.shop.catalog.domain.ProductImage;
import com.pwaquatics.shop.catalog.domain.ProductVariant;
import com.pwaquatics.shop.catalog.infrastructure.ProductVariantRepository;
import com.pwaquatics.shop.common.text.ContentLanguage;
import com.pwaquatics.shop.media.application.ImageView;
import com.pwaquatics.shop.media.application.MediaUrls;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read access to the catalog for other modules. Prices, tax rates and availability always
 * come from here — never from a client request.
 */
@Service
@Transactional(readOnly = true)
public class CatalogLookup {

    private final ProductVariantRepository variants;
    private final MediaUrls mediaUrls;

    CatalogLookup(ProductVariantRepository variants, MediaUrls mediaUrls) {
        this.variants = variants;
        this.mediaUrls = mediaUrls;
    }

    /** Snapshots by variant id; ids that do not exist are simply absent from the result. */
    public Map<UUID, VariantSnapshot> variants(Collection<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        return variants.findAllForSale(variantIds).stream()
                .map(this::snapshot)
                .collect(Collectors.toMap(VariantSnapshot::variantId, Function.identity()));
    }

    /** Names are in the request's language (orders keep them as the customer saw them). */
    private VariantSnapshot snapshot(ProductVariant variant) {
        ContentLanguage lang = ContentLanguage.current();
        Product product = variant.getProduct();
        boolean purchasable = product.isPubliclyVisible() && product.isAvailable(variant);
        int maxQuantity = purchasable
                ? product.maxQuantity(variant, ProductViewAssembler.MAX_QUANTITY_PER_LINE)
                : 0;
        ImageView image = product.getImages().stream()
                .min(Comparator.comparingInt(ProductImage::getSortOrder))
                .map(i -> mediaUrls.image(i.getStorageKey(), lang.pick(i.getAltText(), i.getAltTextDe()), i.getWidth(),
                        i.getHeight()))
                .orElse(null);
        String colourHex = product.getColours().stream()
                .min(Comparator.comparingInt(Colour::getSortOrder).thenComparing(Colour::getName))
                .map(Colour::getHexCode)
                .orElse(null);
        return new VariantSnapshot(
                variant.getId(),
                product.getId(),
                product.getSlug(),
                lang.pick(product.getName(), product.getNameDe()),
                lang.pick(variant.getName(), variant.getNameDe()),
                variant.getSku(),
                variant.getPackSize(),
                variant.price(),
                variant.compareAtPrice() != null,
                product.getTaxRateBps(),
                product.isLiveAnimal(),
                purchasable,
                maxQuantity,
                product.getStockUnits(),
                product.getSpecies() == null ? null : product.getSpecies().getScientificName(),
                product.getGrade() == null ? null : lang.pick(product.getGrade().getName(), product.getGrade().getNameDe()),
                product.getCategory().getSlug(),
                colourHex,
                image);
    }
}
