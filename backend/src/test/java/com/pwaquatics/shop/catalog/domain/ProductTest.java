package com.pwaquatics.shop.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pwaquatics.shop.common.error.BusinessRuleException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ProductTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");

    private static Product activeProduct(int stock) {
        Product product = new Product("blue-dream", "Blue Dream", null, new Species("Neocaridina davidi", null, "nd"), null);
        product.addVariant("BD-1", "1 animal", 1, 390, null);
        product.addVariant("BD-10", "10 animals", 10, 3490, 3900L);
        product.addImage("products/blue-dream/1", "Blue Dream on moss", 1600, 2000, NOW);
        product.adjustStock(stock);
        product.activate(NOW);
        return product;
    }

    @Test
    void packsDrawFromOneSharedStock() {
        Product product = activeProduct(12);
        ProductVariant single = product.getVariants().get(0);
        ProductVariant group = product.getVariants().get(1);

        assertThat(product.maxQuantity(single, 20)).isEqualTo(12);
        assertThat(product.maxQuantity(group, 20)).isEqualTo(1);
        assertThat(product.maxQuantity(single, 5)).isEqualTo(5); // capped
    }

    @Test
    void availabilityFollowsStockAndPackSizes() {
        assertThat(activeProduct(30).availability()).isEqualTo(Availability.IN_STOCK);
        assertThat(activeProduct(5).availability()).isEqualTo(Availability.LOW_STOCK);

        Product onlySinglesLeft = activeProduct(9);
        assertThat(onlySinglesLeft.isAvailable(onlySinglesLeft.getVariants().get(1))).isFalse();
        assertThat(onlySinglesLeft.availability().purchasable()).isTrue();

        Product soldOut = activeProduct(0);
        assertThat(soldOut.availability()).isEqualTo(Availability.OUT_OF_STOCK);
        soldOut.setAvailableFrom(LocalDate.of(2026, 12, 1));
        assertThat(soldOut.availability()).isEqualTo(Availability.COMING_SOON);
    }

    @Test
    void cheapestActiveVariantDefinesTheFromPrice() {
        assertThat(activeProduct(10).priceFrom()).hasValueSatisfying(price -> {
            assertThat(price.amount()).isEqualTo(390);
            assertThat(price.currency()).isEqualTo("EUR");
        });
        assertThat(activeProduct(10).getMinPriceCents()).isEqualTo(390);
    }

    @Test
    void stockCannotBecomeNegative() {
        Product product = activeProduct(3);
        assertThatThrownBy(() -> product.adjustStock(-4))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("negative");
        assertThat(product.getStockUnits()).isEqualTo(3);
    }

    @Test
    void restrictedSpeciesCannotBeActivated() {
        Species invasive = new Species("Procambarus virginalis", "Marbled crayfish", "pv");
        invasive.restrictTrade("EU invasive alien species");
        Product product = new Product("marbled", "Marbled crayfish", null, invasive, null);
        product.addVariant("PV-1", "1 animal", 1, 1000, null);

        assertThatThrownBy(() -> product.activate(NOW))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("SPECIES_RESTRICTED");
        assertThat(product.isPubliclyVisible()).isFalse();
    }

    @Test
    void aProductNeedsAPackSizeAndAPhotoBeforeActivation() {
        Product product = new Product("empty", "Empty", null, null, null);
        assertThat(product.publishProblems())
                .containsExactly(PublishProblem.NO_ACTIVE_VARIANT, PublishProblem.NO_IMAGE);
        assertThatThrownBy(() -> product.activate(NOW))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("NO_ACTIVE_VARIANT");

        product.addVariant("E-1", "1 animal", 1, 500, null);
        assertThatThrownBy(() -> product.activate(NOW)).extracting("code").isEqualTo("NO_IMAGE");

        product.addImage("products/empty/1", "Photo", 1200, 900, NOW);
        product.activate(NOW);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(product.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void aPublishedProductKeepsAtLeastOnePhotoAndPhotosCanBeReordered() {
        Product product = activeProduct(10);
        ProductImage first = product.getImages().getFirst();
        assertThatThrownBy(() -> product.removeImage(first, NOW)).extracting("code").isEqualTo("LAST_IMAGE");

        ProductImage second = product.addImage("products/blue-dream/2", "Close-up", 1600, 1600, NOW);
        product.reorderImages(java.util.List.of(second.getId(), first.getId()), NOW);
        assertThat(product.getImages()).containsExactly(second, first);
        assertThat(product.getImages()).extracting(ProductImage::getSortOrder).containsExactly(0, 1);
        assertThatThrownBy(() -> product.reorderImages(java.util.List.of(second.getId()), NOW))
                .extracting("code").isEqualTo("IMAGE_ORDER_INVALID");

        product.removeImage(first, NOW);
        assertThat(product.getImages()).containsExactly(second);
        assertThat(second.getSortOrder()).isZero();
    }

    @Test
    void variantPricesAreValidatedAndKeepTheFromPriceCurrent() {
        Product product = activeProduct(10);
        ProductVariant single = product.getVariants().getFirst();
        product.updateVariant(single, "BD-1", "1 animal", 1, 450, 500L, true, NOW);
        assertThat(product.getMinPriceCents()).isEqualTo(450);

        assertThatThrownBy(() -> product.updateVariant(single, "BD-1", "1 animal", 1, 450, 400L, true, NOW))
                .extracting("code").isEqualTo("COMPARE_AT_PRICE_INVALID");
        assertThatThrownBy(() -> product.addVariant("BD-0", "none", 0, 100, null))
                .extracting("code").isEqualTo("VARIANT_INVALID");

        // Deactivating the cheaper pack moves the from-price to the next one.
        product.updateVariant(single, "BD-1", "1 animal", 1, 450, null, false, NOW);
        assertThat(product.getMinPriceCents()).isEqualTo(3490);
    }

    @Test
    void aCopyIsAnUnpublishedDraftWithoutStockAndPhotos() {
        Product original = activeProduct(10);
        Product copy = original.copyAsDraft("blue-dream-copy", "Blue Dream (copy)", sku -> sku + "-COPY", NOW);

        assertThat(copy.getStatus()).isEqualTo(ProductStatus.DRAFT);
        assertThat(copy.getStockUnits()).isZero();
        assertThat(copy.getImages()).isEmpty();
        assertThat(copy.getVariants()).extracting(ProductVariant::getSku).containsExactly("BD-1-COPY", "BD-10-COPY");
        assertThat(copy.getMinPriceCents()).isEqualTo(390);
        assertThat(copy.getSpecies()).isSameAs(original.getSpecies());
    }
}
