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
    void aProductNeedsAVariantBeforeActivation() {
        Product product = new Product("empty", "Empty", null, null, null);
        assertThatThrownBy(() -> product.activate(NOW)).isInstanceOf(BusinessRuleException.class);
    }
}
