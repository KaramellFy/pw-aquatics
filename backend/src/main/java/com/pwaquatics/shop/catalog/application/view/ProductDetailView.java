package com.pwaquatics.shop.catalog.application.view;

import com.pwaquatics.shop.catalog.domain.Availability;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.media.application.ImageView;
import jakarta.annotation.Nullable;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Everything the product page needs. HTML fields are sanitised server-side. */
public record ProductDetailView(
        UUID id,
        String slug,
        String name,
        @Nullable String shortDescription,
        @Nullable String descriptionHtml,
        @Nullable String careNotesHtml,
        @Nullable SpeciesView species,
        List<CategoryRef> categoryPath,
        @Nullable GradeRef grade,
        List<ColourView> colours,
        @Nullable Money priceFrom,
        Availability availability,
        @Nullable LocalDate availableFrom,
        boolean liveAnimal,
        List<VariantView> variants,
        List<ImageView> images,
        SeoView seo) {}
