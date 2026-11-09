package com.pwaquatics.shop.catalog.api;

import com.pwaquatics.shop.catalog.application.CatalogQueryService;
import com.pwaquatics.shop.catalog.application.view.CategoryNodeView;
import com.pwaquatics.shop.catalog.application.view.ColourView;
import com.pwaquatics.shop.catalog.application.view.FacetsView;
import com.pwaquatics.shop.catalog.application.view.GradeView;
import com.pwaquatics.shop.catalog.application.view.ProductDetailView;
import com.pwaquatics.shop.catalog.application.view.ProductSummaryView;
import com.pwaquatics.shop.catalog.application.view.SpeciesSummaryView;
import com.pwaquatics.shop.catalog.application.view.SpeciesView;
import com.pwaquatics.shop.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, read-only catalog (docs/API.md §2.2). */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Catalog")
class CatalogController {

    private final CatalogQueryService catalog;

    CatalogController(CatalogQueryService catalog) {
        this.catalog = catalog;
    }

    @Operation(summary = "Search and filter products")
    @GetMapping("/products")
    PageResponse<ProductSummaryView> products(@Valid @ParameterObject ProductSearchParams params) {
        return PageResponse.of(catalog.searchProducts(params.toSearch()));
    }

    @Operation(summary = "Filter options with counts for the current search")
    @GetMapping("/products/facets")
    FacetsView facets(@Valid @ParameterObject ProductSearchParams params) {
        return catalog.facets(params.toSearch());
    }

    @Operation(summary = "Product detail")
    @GetMapping("/products/{slug}")
    ProductDetailView product(@PathVariable @Size(max = 180) String slug) {
        return catalog.product(slug);
    }

    @Operation(summary = "Related products (same species, then same category)")
    @GetMapping("/products/{slug}/related")
    List<ProductSummaryView> related(@PathVariable @Size(max = 180) String slug) {
        return catalog.related(slug);
    }

    @Operation(summary = "Category tree")
    @GetMapping("/categories")
    List<CategoryNodeView> categories() {
        return catalog.categoryTree();
    }

    @Operation(summary = "Species list")
    @GetMapping("/species")
    List<SpeciesSummaryView> species() {
        return catalog.species();
    }

    @Operation(summary = "Species profile with care parameters")
    @GetMapping("/species/{slug}")
    SpeciesView species(@PathVariable @Size(max = 170) String slug) {
        return catalog.species(slug);
    }

    @Operation(summary = "Colours")
    @GetMapping("/colours")
    List<ColourView> colours() {
        return catalog.colours();
    }

    @Operation(summary = "Grades")
    @GetMapping("/grades")
    List<GradeView> grades() {
        return catalog.grades();
    }
}
