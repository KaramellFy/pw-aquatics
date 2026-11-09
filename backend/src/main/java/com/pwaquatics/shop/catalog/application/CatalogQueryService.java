package com.pwaquatics.shop.catalog.application;

import com.pwaquatics.shop.catalog.application.view.CategoryNodeView;
import com.pwaquatics.shop.catalog.application.view.ColourView;
import com.pwaquatics.shop.catalog.application.view.FacetsView;
import com.pwaquatics.shop.catalog.application.view.FacetsView.FacetOption;
import com.pwaquatics.shop.catalog.application.view.GradeView;
import com.pwaquatics.shop.catalog.application.view.ProductDetailView;
import com.pwaquatics.shop.catalog.application.view.ProductSummaryView;
import com.pwaquatics.shop.catalog.application.view.SpeciesSummaryView;
import com.pwaquatics.shop.catalog.application.view.SpeciesView;
import com.pwaquatics.shop.catalog.domain.Category;
import com.pwaquatics.shop.catalog.domain.Product;
import com.pwaquatics.shop.catalog.domain.ProductQuery;
import com.pwaquatics.shop.catalog.infrastructure.CategoryRepository;
import com.pwaquatics.shop.catalog.infrastructure.ColourRepository;
import com.pwaquatics.shop.catalog.infrastructure.GradeRepository;
import com.pwaquatics.shop.catalog.infrastructure.ProductRepository;
import com.pwaquatics.shop.catalog.infrastructure.ProductSearchRepository;
import com.pwaquatics.shop.catalog.infrastructure.ProductSearchRepository.FacetCount;
import com.pwaquatics.shop.catalog.infrastructure.SpeciesRepository;
import com.pwaquatics.shop.common.error.NotFoundException;
import com.pwaquatics.shop.common.money.Money;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only queries for the public catalog. */
@Service
@Transactional(readOnly = true)
public class CatalogQueryService {

    private static final int RELATED_LIMIT = 4;

    private final ProductSearchRepository search;
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final SpeciesRepository species;
    private final ColourRepository colours;
    private final GradeRepository grades;
    private final ProductViewAssembler assembler;
    private final CatalogViewMapper mapper;

    CatalogQueryService(
            ProductSearchRepository search,
            ProductRepository products,
            CategoryRepository categories,
            SpeciesRepository species,
            ColourRepository colours,
            GradeRepository grades,
            ProductViewAssembler assembler,
            CatalogViewMapper mapper) {
        this.search = search;
        this.products = products;
        this.categories = categories;
        this.species = species;
        this.colours = colours;
        this.grades = grades;
        this.assembler = assembler;
        this.mapper = mapper;
    }

    public Page<ProductSummaryView> searchProducts(ProductSearch request) {
        return search.search(toQuery(request), request.sort(), request.page(), request.size())
                .map(assembler::summary);
    }

    public FacetsView facets(ProductSearch request) {
        ProductQuery query = toQuery(request);
        ProductSearchRepository.AvailabilityCounts availability = search.availabilityFacet(query);
        ProductSearchRepository.PriceBounds price = search.priceFacet(query);
        return new FacetsView(
                options(search.speciesFacet(query)),
                options(search.colourFacet(query)),
                options(search.gradeFacet(query)),
                new FacetsView.AvailabilityCounts(availability.inStock(), availability.comingSoon()),
                price == null ? null : new FacetsView.PriceRange(Money.eur(price.minCents()), Money.eur(price.maxCents())));
    }

    public ProductDetailView product(String slug) {
        return assembler.detail(findVisible(slug));
    }

    public List<ProductSummaryView> related(String slug) {
        Product product = findVisible(slug);
        UUID speciesId = product.getSpecies() == null ? null : product.getSpecies().getId();
        List<UUID> ids = products.findRelatedIds(
                product.getId(), speciesId, product.getCategory().getId(), Limit.of(RELATED_LIMIT));
        return search.loadInOrder(ids).stream().map(assembler::summary).toList();
    }

    public List<CategoryNodeView> categoryTree() {
        List<Category> all = categories.findAllActive();
        Map<UUID, List<Category>> byParent = all.stream()
                .filter(category -> category.getParent() != null)
                .collect(Collectors.groupingBy(category -> category.getParent().getId()));
        return all.stream()
                .filter(category -> category.getParent() == null)
                .map(category -> node(category, byParent))
                .toList();
    }

    public List<SpeciesSummaryView> species() {
        return species.findByTradeRestrictedFalseOrderByScientificNameAsc().stream().map(mapper::toSummary).toList();
    }

    public SpeciesView species(String slug) {
        return species.findBySlugAndTradeRestrictedFalse(slug)
                .map(mapper::toView)
                .orElseThrow(() -> new NotFoundException("Species not found."));
    }

    public List<ColourView> colours() {
        return colours.findAllByOrderBySortOrderAscNameAsc().stream().map(mapper::toView).toList();
    }

    public List<GradeView> grades() {
        return grades.findAllByOrderBySortOrderAscRankAsc().stream().map(mapper::toView).toList();
    }

    private Product findVisible(String slug) {
        return products.findVisibleBySlug(slug).orElseThrow(() -> new NotFoundException("Product not found."));
    }

    private ProductQuery toQuery(ProductSearch request) {
        return new ProductQuery(
                request.text(),
                categorySubtree(request.category()),
                request.species(),
                request.colours(),
                request.grades(),
                request.minPrice(),
                request.maxPrice(),
                request.availability(),
                request.featuredOnly());
    }

    /** The category with all its descendants; {@code null} = no category filter; empty = unknown slug. */
    private Collection<UUID> categorySubtree(String slug) {
        if (slug == null || slug.isBlank()) {
            return null;
        }
        List<Category> all = categories.findAllActive();
        Category start = all.stream().filter(c -> c.getSlug().equals(slug)).findFirst().orElse(null);
        if (start == null) {
            return Set.of();
        }
        Set<UUID> ids = new HashSet<>();
        Deque<Category> queue = new ArrayDeque<>(List.of(start));
        while (!queue.isEmpty()) {
            Category current = queue.pop();
            ids.add(current.getId());
            all.stream()
                    .filter(c -> c.getParent() != null && Objects.equals(c.getParent().getId(), current.getId()))
                    .forEach(queue::push);
        }
        return ids;
    }

    private CategoryNodeView node(Category category, Map<UUID, List<Category>> byParent) {
        List<CategoryNodeView> children = byParent.getOrDefault(category.getId(), List.of()).stream()
                .map(child -> node(child, byParent))
                .toList();
        return new CategoryNodeView(category.getSlug(), category.getName(), category.getDescription(), children);
    }

    private static List<FacetOption> options(List<FacetCount> counts) {
        return counts.stream().map(c -> new FacetOption(c.slug(), c.name(), c.hexCode(), c.count())).toList();
    }
}
