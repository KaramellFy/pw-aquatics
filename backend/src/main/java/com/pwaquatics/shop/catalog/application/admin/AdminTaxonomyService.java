package com.pwaquatics.shop.catalog.application.admin;

import com.pwaquatics.shop.audit.application.Actor;
import com.pwaquatics.shop.audit.application.AuditService;
import com.pwaquatics.shop.catalog.application.admin.view.AdminCategoryView;
import com.pwaquatics.shop.catalog.application.admin.view.AdminColourView;
import com.pwaquatics.shop.catalog.application.admin.view.AdminGradeView;
import com.pwaquatics.shop.catalog.application.admin.view.AdminSpeciesView;
import com.pwaquatics.shop.catalog.domain.Category;
import com.pwaquatics.shop.catalog.domain.Colour;
import com.pwaquatics.shop.catalog.domain.Grade;
import com.pwaquatics.shop.catalog.domain.Species;
import com.pwaquatics.shop.catalog.domain.SpeciesData;
import com.pwaquatics.shop.catalog.infrastructure.CategoryRepository;
import com.pwaquatics.shop.catalog.infrastructure.ColourRepository;
import com.pwaquatics.shop.catalog.infrastructure.GradeRepository;
import com.pwaquatics.shop.catalog.infrastructure.ProductRepository;
import com.pwaquatics.shop.catalog.infrastructure.SpeciesRepository;
import com.pwaquatics.shop.common.error.ConflictException;
import com.pwaquatics.shop.common.error.NotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Categories, species, colours and grades for the admin area (docs/API.md §3.3). Every
 * change is recorded in the audit log. Something still used by a product cannot be deleted.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminTaxonomyService {

    private final CategoryRepository categories;
    private final SpeciesRepository species;
    private final ColourRepository colours;
    private final GradeRepository grades;
    private final ProductRepository products;
    private final AuditService audit;

    AdminTaxonomyService(
            CategoryRepository categories,
            SpeciesRepository species,
            ColourRepository colours,
            GradeRepository grades,
            ProductRepository products,
            AuditService audit) {
        this.categories = categories;
        this.species = species;
        this.colours = colours;
        this.grades = grades;
        this.products = products;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ categories

    @Transactional(readOnly = true)
    public List<AdminCategoryView> categories() {
        Map<UUID, Long> counts = counts(products.countByCategory());
        return categories.findAllForAdmin().stream().map(category -> toView(category, counts)).toList();
    }

    @Transactional
    public AdminCategoryView createCategory(CategoryInput input, Actor actor) {
        String slug = AdminSlugs.resolve(input.slug(), input.name(), 120, categories::existsBySlug);
        Category category = new Category(input.name(), slug);
        category.update(input.name(), slug, parent(input.parentId()), input.description(), input.sortOrder(),
                input.active());
        categories.save(category);
        audit.record(actor, "CATEGORY_CREATED", "CATEGORY", category.getId(), Map.of("slug", slug));
        return toView(category, counts(products.countByCategory()));
    }

    @Transactional
    public AdminCategoryView updateCategory(UUID id, CategoryInput input, Actor actor) {
        Category category = categories.findById(id).orElseThrow(() -> notFound("Category"));
        String slug = AdminSlugs.resolve(input.slug(), input.name(), 120, s -> categories.existsBySlugAndIdNot(s, id));
        category.update(input.name(), slug, parent(input.parentId()), input.description(), input.sortOrder(),
                input.active());
        audit.record(actor, "CATEGORY_UPDATED", "CATEGORY", id, Map.of("slug", slug));
        return toView(category, counts(products.countByCategory()));
    }

    @Transactional
    public void deleteCategory(UUID id, Actor actor) {
        Category category = categories.findById(id).orElseThrow(() -> notFound("Category"));
        if (products.existsByCategoryId(id) || categories.existsByParentId(id)) {
            throw inUse("This category still contains products or sub-categories.");
        }
        categories.delete(category);
        audit.record(actor, "CATEGORY_DELETED", "CATEGORY", id, Map.of("slug", category.getSlug()));
    }

    private Category parent(UUID parentId) {
        return parentId == null ? null : categories.findById(parentId).orElseThrow(() -> notFound("Parent category"));
    }

    private static AdminCategoryView toView(Category category, Map<UUID, Long> counts) {
        return new AdminCategoryView(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getParent() == null ? null : category.getParent().getId(),
                category.getDescription(),
                category.getSortOrder(),
                category.isActive(),
                counts.getOrDefault(category.getId(), 0L));
    }

    // ------------------------------------------------------------------ species

    @Transactional(readOnly = true)
    public List<AdminSpeciesView> species() {
        Map<UUID, Long> counts = counts(products.countBySpecies());
        return species.findAllByOrderByScientificNameAsc().stream().map(s -> toView(s, counts)).toList();
    }

    @Transactional(readOnly = true)
    public AdminSpeciesView speciesDetail(UUID id) {
        return toView(species.findById(id).orElseThrow(() -> notFound("Species")), counts(products.countBySpecies()));
    }

    @Transactional
    public AdminSpeciesView createSpecies(SpeciesData input, Actor actor) {
        if (species.existsByScientificNameIgnoreCase(input.scientificName())) {
            throw new ConflictException("SCIENTIFIC_NAME_TAKEN", "This species already exists.");
        }
        String slug = AdminSlugs.resolve(input.slug(), input.scientificName(), 170, species::existsBySlug);
        Species created = new Species(input.scientificName(), input.commonName(), slug);
        created.update(withSlug(input, slug));
        species.save(created);
        audit.record(actor, "SPECIES_CREATED", "SPECIES", created.getId(), Map.of("slug", slug));
        return toView(created, counts(products.countBySpecies()));
    }

    /**
     * Changes a species. Marking it as trade-restricted removes its products from the shop at
     * once (they are filtered out everywhere) and blocks publishing them.
     */
    @Transactional
    public AdminSpeciesView updateSpecies(UUID id, SpeciesData input, Actor actor) {
        Species existing = species.findById(id).orElseThrow(() -> notFound("Species"));
        if (species.existsByScientificNameIgnoreCaseAndIdNot(input.scientificName(), id)) {
            throw new ConflictException("SCIENTIFIC_NAME_TAKEN", "This species already exists.");
        }
        String slug = AdminSlugs.resolve(input.slug(), input.scientificName(), 170,
                s -> species.existsBySlugAndIdNot(s, id));
        boolean wasRestricted = existing.isTradeRestricted();
        existing.update(withSlug(input, slug));
        audit.record(actor, "SPECIES_UPDATED", "SPECIES", id, Map.of("slug", slug));
        if (existing.isTradeRestricted() != wasRestricted) {
            audit.record(actor, existing.isTradeRestricted() ? "SPECIES_TRADE_RESTRICTED" : "SPECIES_TRADE_ALLOWED",
                    "SPECIES", id, Map.of("slug", slug));
        }
        return toView(existing, counts(products.countBySpecies()));
    }

    @Transactional
    public void deleteSpecies(UUID id, Actor actor) {
        Species existing = species.findById(id).orElseThrow(() -> notFound("Species"));
        if (products.existsBySpeciesId(id)) {
            throw inUse("Products of this species still exist.");
        }
        species.delete(existing);
        audit.record(actor, "SPECIES_DELETED", "SPECIES", id, Map.of("slug", existing.getSlug()));
    }

    private static SpeciesData withSlug(SpeciesData data, String slug) {
        return new SpeciesData(data.scientificName(), data.commonName(), slug, data.description(), data.origin(),
                data.tempMinC(), data.tempMaxC(), data.phMin(), data.phMax(), data.ghMin(), data.ghMax(),
                data.khMin(), data.khMax(), data.tdsMin(), data.tdsMax(), data.adultSizeMm(), data.lifespanMonths(),
                data.difficulty(), data.tradeRestricted(), data.restrictionNote());
    }

    private static AdminSpeciesView toView(Species s, Map<UUID, Long> counts) {
        return new AdminSpeciesView(s.getId(), s.getScientificName(), s.getCommonName(), s.getSlug(),
                s.getDescription(), s.getOrigin(), s.getTempMinC(), s.getTempMaxC(), s.getPhMin(), s.getPhMax(),
                s.getGhMin(), s.getGhMax(), s.getKhMin(), s.getKhMax(), s.getTdsMin(), s.getTdsMax(),
                s.getAdultSizeMm(), s.getLifespanMonths(), s.getDifficulty(), s.isTradeRestricted(),
                s.getRestrictionNote(), counts.getOrDefault(s.getId(), 0L));
    }

    // ------------------------------------------------------------------ colours

    @Transactional(readOnly = true)
    public List<AdminColourView> colours() {
        Map<UUID, Long> counts = counts(products.countByColour());
        return colours.findAllByOrderBySortOrderAscNameAsc().stream().map(c -> toView(c, counts)).toList();
    }

    @Transactional
    public AdminColourView createColour(ColourInput input, Actor actor) {
        String slug = AdminSlugs.resolve(input.slug(), input.name(), 80, colours::existsBySlug);
        Colour colour = new Colour(input.name(), slug);
        colour.update(input.name(), slug, normaliseHex(input.hexCode()), input.sortOrder());
        colours.save(colour);
        audit.record(actor, "COLOUR_CREATED", "COLOUR", colour.getId(), Map.of("slug", slug));
        return toView(colour, Map.of());
    }

    @Transactional
    public AdminColourView updateColour(UUID id, ColourInput input, Actor actor) {
        Colour colour = colours.findById(id).orElseThrow(() -> notFound("Colour"));
        String slug = AdminSlugs.resolve(input.slug(), input.name(), 80, s -> colours.existsBySlugAndIdNot(s, id));
        colour.update(input.name(), slug, normaliseHex(input.hexCode()), input.sortOrder());
        audit.record(actor, "COLOUR_UPDATED", "COLOUR", id, Map.of("slug", slug));
        return toView(colour, counts(products.countByColour()));
    }

    @Transactional
    public void deleteColour(UUID id, Actor actor) {
        Colour colour = colours.findById(id).orElseThrow(() -> notFound("Colour"));
        if (products.existsByColourId(id)) {
            throw inUse("Products still use this colour.");
        }
        colours.delete(colour);
        audit.record(actor, "COLOUR_DELETED", "COLOUR", id, Map.of("slug", colour.getSlug()));
    }

    private static String normaliseHex(String hexCode) {
        return hexCode == null || hexCode.isBlank() ? null : hexCode.toUpperCase(Locale.ROOT);
    }

    private static AdminColourView toView(Colour colour, Map<UUID, Long> counts) {
        return new AdminColourView(colour.getId(), colour.getName(), colour.getSlug(), colour.getHexCode(),
                colour.getSortOrder(), counts.getOrDefault(colour.getId(), 0L));
    }

    // ------------------------------------------------------------------ grades

    @Transactional(readOnly = true)
    public List<AdminGradeView> grades() {
        Map<UUID, Long> counts = counts(products.countByGrade());
        return grades.findAllByOrderBySortOrderAscRankAsc().stream().map(g -> toView(g, counts)).toList();
    }

    @Transactional
    public AdminGradeView createGrade(GradeInput input, Actor actor) {
        String slug = AdminSlugs.resolve(input.slug(), input.name(), 80, grades::existsBySlug);
        Grade grade = new Grade(input.name(), slug);
        grade.update(input.name(), slug, input.scale(), input.description(), input.rank(), input.sortOrder());
        grades.save(grade);
        audit.record(actor, "GRADE_CREATED", "GRADE", grade.getId(), Map.of("slug", slug));
        return toView(grade, Map.of());
    }

    @Transactional
    public AdminGradeView updateGrade(UUID id, GradeInput input, Actor actor) {
        Grade grade = grades.findById(id).orElseThrow(() -> notFound("Grade"));
        String slug = AdminSlugs.resolve(input.slug(), input.name(), 80, s -> grades.existsBySlugAndIdNot(s, id));
        grade.update(input.name(), slug, input.scale(), input.description(), input.rank(), input.sortOrder());
        audit.record(actor, "GRADE_UPDATED", "GRADE", id, Map.of("slug", slug));
        return toView(grade, counts(products.countByGrade()));
    }

    @Transactional
    public void deleteGrade(UUID id, Actor actor) {
        Grade grade = grades.findById(id).orElseThrow(() -> notFound("Grade"));
        if (products.existsByGradeId(id)) {
            throw inUse("Products still use this grade.");
        }
        grades.delete(grade);
        audit.record(actor, "GRADE_DELETED", "GRADE", id, Map.of("slug", grade.getSlug()));
    }

    private static AdminGradeView toView(Grade grade, Map<UUID, Long> counts) {
        return new AdminGradeView(grade.getId(), grade.getName(), grade.getSlug(), grade.getScale(),
                grade.getDescription(), grade.getRank(), grade.getSortOrder(), counts.getOrDefault(grade.getId(), 0L));
    }

    // ------------------------------------------------------------------ helpers

    private static Map<UUID, Long> counts(List<Object[]> rows) {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    private static NotFoundException notFound(String what) {
        return new NotFoundException(what + " not found.");
    }

    private static ConflictException inUse(String message) {
        return new ConflictException("IN_USE", message);
    }
}
