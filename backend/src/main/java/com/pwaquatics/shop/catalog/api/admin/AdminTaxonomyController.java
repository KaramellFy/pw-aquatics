package com.pwaquatics.shop.catalog.api.admin;

import static com.pwaquatics.shop.common.text.Texts.trimToNull;

import com.pwaquatics.shop.audit.application.Actor;
import com.pwaquatics.shop.catalog.api.admin.AdminRequests.CategoryRequest;
import com.pwaquatics.shop.catalog.api.admin.AdminRequests.ColourRequest;
import com.pwaquatics.shop.catalog.api.admin.AdminRequests.GradeRequest;
import com.pwaquatics.shop.catalog.api.admin.AdminRequests.SpeciesRequest;
import com.pwaquatics.shop.catalog.application.admin.AdminTaxonomyService;
import com.pwaquatics.shop.catalog.application.admin.CategoryInput;
import com.pwaquatics.shop.catalog.application.admin.ColourInput;
import com.pwaquatics.shop.catalog.application.admin.GradeInput;
import com.pwaquatics.shop.catalog.application.admin.view.AdminCategoryView;
import com.pwaquatics.shop.catalog.application.admin.view.AdminColourView;
import com.pwaquatics.shop.catalog.application.admin.view.AdminGradeView;
import com.pwaquatics.shop.catalog.application.admin.view.AdminSpeciesView;
import com.pwaquatics.shop.catalog.domain.SpeciesData;
import com.pwaquatics.shop.security.ShopUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Categories, species, colours and grades (docs/API.md §3.3). Role ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin: taxonomy")
class AdminTaxonomyController {

    private final AdminTaxonomyService taxonomy;

    AdminTaxonomyController(AdminTaxonomyService taxonomy) {
        this.taxonomy = taxonomy;
    }

    // ------------------------------------------------------------------ categories

    @Operation(summary = "All categories (flat; parentId builds the tree)")
    @GetMapping("/categories")
    List<AdminCategoryView> categories() {
        return taxonomy.categories();
    }

    @Operation(summary = "Create a category")
    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    AdminCategoryView createCategory(
            @Valid @RequestBody CategoryRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return taxonomy.createCategory(input(request), actor(principal, http));
    }

    @Operation(summary = "Change a category")
    @PutMapping("/categories/{id}")
    AdminCategoryView updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return taxonomy.updateCategory(id, input(request), actor(principal, http));
    }

    @Operation(summary = "Delete an empty category")
    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteCategory(
            @PathVariable UUID id, @AuthenticationPrincipal ShopUserPrincipal principal, HttpServletRequest http) {
        taxonomy.deleteCategory(id, actor(principal, http));
    }

    private static CategoryInput input(CategoryRequest r) {
        return new CategoryInput(r.name().strip(), trimToNull(r.slug()), r.parentId(), trimToNull(r.description()),
                r.sortOrder(), r.active());
    }

    // ------------------------------------------------------------------ species

    @Operation(summary = "All species, including trade-restricted ones")
    @GetMapping("/species")
    List<AdminSpeciesView> species() {
        return taxonomy.species();
    }

    @Operation(summary = "One species with all care data")
    @GetMapping("/species/{id}")
    AdminSpeciesView speciesDetail(@PathVariable UUID id) {
        return taxonomy.speciesDetail(id);
    }

    @Operation(summary = "Create a species")
    @PostMapping("/species")
    @ResponseStatus(HttpStatus.CREATED)
    AdminSpeciesView createSpecies(
            @Valid @RequestBody SpeciesRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return taxonomy.createSpecies(input(request), actor(principal, http));
    }

    @Operation(summary = "Change a species (incl. trade restriction)")
    @PutMapping("/species/{id}")
    AdminSpeciesView updateSpecies(
            @PathVariable UUID id,
            @Valid @RequestBody SpeciesRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return taxonomy.updateSpecies(id, input(request), actor(principal, http));
    }

    @Operation(summary = "Delete a species without products")
    @DeleteMapping("/species/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteSpecies(
            @PathVariable UUID id, @AuthenticationPrincipal ShopUserPrincipal principal, HttpServletRequest http) {
        taxonomy.deleteSpecies(id, actor(principal, http));
    }

    private static SpeciesData input(SpeciesRequest r) {
        return new SpeciesData(r.scientificName().strip(), trimToNull(r.commonName()), trimToNull(r.slug()),
                trimToNull(r.description()), trimToNull(r.origin()), r.tempMinC(), r.tempMaxC(), r.phMin(),
                r.phMax(), r.ghMin(), r.ghMax(), r.khMin(), r.khMax(), r.tdsMin(), r.tdsMax(), r.adultSizeMm(),
                r.lifespanMonths(), r.difficulty(), r.tradeRestricted(), trimToNull(r.restrictionNote()));
    }

    // ------------------------------------------------------------------ colours

    @Operation(summary = "All colours")
    @GetMapping("/colours")
    List<AdminColourView> colours() {
        return taxonomy.colours();
    }

    @Operation(summary = "Create a colour")
    @PostMapping("/colours")
    @ResponseStatus(HttpStatus.CREATED)
    AdminColourView createColour(
            @Valid @RequestBody ColourRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return taxonomy.createColour(input(request), actor(principal, http));
    }

    @Operation(summary = "Change a colour")
    @PutMapping("/colours/{id}")
    AdminColourView updateColour(
            @PathVariable UUID id,
            @Valid @RequestBody ColourRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return taxonomy.updateColour(id, input(request), actor(principal, http));
    }

    @Operation(summary = "Delete an unused colour")
    @DeleteMapping("/colours/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteColour(
            @PathVariable UUID id, @AuthenticationPrincipal ShopUserPrincipal principal, HttpServletRequest http) {
        taxonomy.deleteColour(id, actor(principal, http));
    }

    private static ColourInput input(ColourRequest r) {
        return new ColourInput(r.name().strip(), trimToNull(r.slug()), trimToNull(r.hexCode()), r.sortOrder());
    }

    // ------------------------------------------------------------------ grades

    @Operation(summary = "All grades")
    @GetMapping("/grades")
    List<AdminGradeView> grades() {
        return taxonomy.grades();
    }

    @Operation(summary = "Create a grade")
    @PostMapping("/grades")
    @ResponseStatus(HttpStatus.CREATED)
    AdminGradeView createGrade(
            @Valid @RequestBody GradeRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return taxonomy.createGrade(input(request), actor(principal, http));
    }

    @Operation(summary = "Change a grade")
    @PutMapping("/grades/{id}")
    AdminGradeView updateGrade(
            @PathVariable UUID id,
            @Valid @RequestBody GradeRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return taxonomy.updateGrade(id, input(request), actor(principal, http));
    }

    @Operation(summary = "Delete an unused grade")
    @DeleteMapping("/grades/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteGrade(
            @PathVariable UUID id, @AuthenticationPrincipal ShopUserPrincipal principal, HttpServletRequest http) {
        taxonomy.deleteGrade(id, actor(principal, http));
    }

    private static GradeInput input(GradeRequest r) {
        return new GradeInput(r.name().strip(), trimToNull(r.slug()), trimToNull(r.scale()),
                trimToNull(r.description()), r.rank(), r.sortOrder());
    }

    static Actor actor(ShopUserPrincipal principal, HttpServletRequest http) {
        return new Actor(principal.userId(), http.getRemoteAddr());
    }
}
