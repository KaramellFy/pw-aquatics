package com.pwaquatics.shop.catalog.infrastructure;

import com.pwaquatics.shop.catalog.domain.AvailabilityFilter;
import com.pwaquatics.shop.catalog.domain.Colour;
import com.pwaquatics.shop.catalog.domain.Grade;
import com.pwaquatics.shop.catalog.domain.Product;
import com.pwaquatics.shop.catalog.domain.ProductQuery;
import com.pwaquatics.shop.catalog.domain.ProductQuery.Dimension;
import com.pwaquatics.shop.catalog.domain.ProductSort;
import com.pwaquatics.shop.catalog.domain.ProductStatus;
import com.pwaquatics.shop.catalog.domain.ProductVariant;
import com.pwaquatics.shop.catalog.domain.Species;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/**
 * Catalog search and facet counts with the JPA Criteria API. All queries share the same
 * filter rules ({@link Filters}), so lists and facet counts can never disagree.
 */
@Repository
public class ProductSearchRepository {

    private final EntityManager em;

    public ProductSearchRepository(EntityManager em) {
        this.em = em;
    }

    /** One page of visible products matching {@code query}, fully loaded for display. */
    public Page<Product> search(ProductQuery query, ProductSort sort, int page, int size) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        // 1) ids of the requested page, in the requested order
        CriteriaQuery<UUID> idQuery = cb.createQuery(UUID.class);
        Root<Product> root = idQuery.from(Product.class);
        Filters filters = new Filters(cb, idQuery, root);
        idQuery.select(root.get("id"))
                .where(filters.build(query, null))
                .orderBy(orders(cb, root, filters, sort));
        List<UUID> ids = em.createQuery(idQuery)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();

        // 2) total count
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Product> countRoot = countQuery.from(Product.class);
        countQuery.select(cb.count(countRoot)).where(new Filters(cb, countQuery, countRoot).build(query, null));
        long total = em.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(loadInOrder(ids), PageRequest.of(page, size), total);
    }

    /** Products by id with their to-one associations fetched; collections load in batches. */
    public List<Product> loadInOrder(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, Product> byId = em.createQuery("""
                        SELECT p FROM Product p
                        LEFT JOIN FETCH p.species
                        LEFT JOIN FETCH p.category
                        LEFT JOIN FETCH p.grade
                        WHERE p.id IN :ids
                        """, Product.class)
                .setParameter("ids", ids)
                .getResultList()
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return ids.stream().map(byId::get).toList();
    }

    public List<FacetCount> speciesFacet(ProductQuery query) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<Product> root = cq.from(Product.class);
        Filters filters = new Filters(cb, cq, root);
        Join<Product, Species> species = filters.species();
        cq.select(cb.tuple(species.get("slug"), cb.coalesce(species.get("commonName"), species.get("scientificName")),
                        cb.countDistinct(root)))
                .where(cb.and(filters.build(query, Dimension.SPECIES), cb.isNotNull(species.get("id"))))
                .groupBy(species.get("slug"), species.get("commonName"), species.get("scientificName"))
                .orderBy(cb.asc(species.get("scientificName")));
        return em.createQuery(cq).getResultList().stream()
                .map(t -> new FacetCount(t.get(0, String.class), t.get(1, String.class), null, t.get(2, Long.class)))
                .toList();
    }

    public List<FacetCount> colourFacet(ProductQuery query) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<Product> root = cq.from(Product.class);
        Filters filters = new Filters(cb, cq, root);
        Join<Product, Colour> colour = root.join("colours");
        cq.select(cb.tuple(colour.get("slug"), colour.get("name"), colour.get("hexCode"), cb.countDistinct(root)))
                .where(filters.build(query, Dimension.COLOUR))
                .groupBy(colour.get("slug"), colour.get("name"), colour.get("hexCode"), colour.get("sortOrder"))
                .orderBy(cb.asc(colour.get("sortOrder")), cb.asc(colour.get("name")));
        return em.createQuery(cq).getResultList().stream()
                .map(t -> new FacetCount(
                        t.get(0, String.class), t.get(1, String.class), t.get(2, String.class), t.get(3, Long.class)))
                .toList();
    }

    public List<FacetCount> gradeFacet(ProductQuery query) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<Product> root = cq.from(Product.class);
        Filters filters = new Filters(cb, cq, root);
        Join<Product, Grade> grade = filters.grade();
        cq.select(cb.tuple(grade.get("slug"), grade.get("name"), cb.countDistinct(root)))
                .where(cb.and(filters.build(query, Dimension.GRADE), cb.isNotNull(grade.get("id"))))
                .groupBy(grade.get("slug"), grade.get("name"), grade.get("sortOrder"), grade.get("rank"))
                .orderBy(cb.asc(grade.get("sortOrder")), cb.asc(grade.get("rank")));
        return em.createQuery(cq).getResultList().stream()
                .map(t -> new FacetCount(t.get(0, String.class), t.get(1, String.class), null, t.get(2, Long.class)))
                .toList();
    }

    /** Counts for "in stock" and "coming soon", ignoring the current availability filter. */
    public AvailabilityCounts availabilityFacet(ProductQuery query) {
        return new AvailabilityCounts(
                countWithAvailability(query, AvailabilityFilter.IN_STOCK),
                countWithAvailability(query, AvailabilityFilter.COMING_SOON));
    }

    /** Cheapest and most expensive "from" price, ignoring the current price filter. */
    public PriceBounds priceFacet(ProductQuery query) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<Product> root = cq.from(Product.class);
        Filters filters = new Filters(cb, cq, root);
        Expression<Long> price = root.get("minPriceCents");
        cq.select(cb.tuple(cb.min(price), cb.max(price))).where(filters.build(query, Dimension.PRICE));
        Tuple result = em.createQuery(cq).getSingleResult();
        Long min = result.get(0, Long.class);
        Long max = result.get(1, Long.class);
        return min == null || max == null ? null : new PriceBounds(min, max);
    }

    private long countWithAvailability(ProductQuery query, AvailabilityFilter availability) {
        ProductQuery adjusted = new ProductQuery(query.text(), query.categoryIds(), query.species(), query.colours(),
                query.grades(), query.minPriceCents(), query.maxPriceCents(), availability, query.featuredOnly());
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<Product> root = cq.from(Product.class);
        cq.select(cb.count(root)).where(new Filters(cb, cq, root).build(adjusted, null));
        return em.createQuery(cq).getSingleResult();
    }

    private static List<Order> orders(CriteriaBuilder cb, Root<Product> root, Filters filters, ProductSort sort) {
        List<Order> orders = new ArrayList<>();
        Expression<Long> price = root.get("minPriceCents");
        switch (sort == null ? ProductSort.RELEVANCE : sort) {
            case RELEVANCE -> {
                // Purchasable first, then "coming soon", then sold out; within each: featured, newest.
                orders.add(cb.asc(cb.selectCase()
                        .when(cb.exists(filters.purchasableVariant()), 0)
                        .when(cb.isNotNull(root.get("availableFrom")), 1)
                        .otherwise(2)));
                orders.add(cb.desc(root.get("featured")));
                orders.add(cb.desc(root.get("publishedAt")));
            }
            case NEWEST -> orders.add(cb.desc(root.get("publishedAt")));
            case PRICE_ASC -> orders.add(cb.asc(cb.coalesce(price, Long.MAX_VALUE)));
            case PRICE_DESC -> orders.add(cb.desc(cb.coalesce(price, -1L)));
            case NAME_ASC -> { /* name below */ }
            case GRADE_DESC -> orders.add(cb.desc(cb.coalesce(filters.grade().get("rank"), -1)));
        }
        orders.add(cb.asc(root.get("name")));
        orders.add(cb.asc(root.get("id"))); // stable pagination
        return orders;
    }

    /** A facet option and how many visible products match it. */
    public record FacetCount(String slug, String name, String hexCode, long count) {}

    public record AvailabilityCounts(long inStock, long comingSoon) {}

    public record PriceBounds(long minCents, long maxCents) {}

    /** Builds the shared WHERE clause for one query root. Joins are created once and reused. */
    private static final class Filters {

        private final CriteriaBuilder cb;
        private final AbstractQuery<?> query;
        private final Root<Product> root;
        private Join<Product, Species> species;
        private Join<Product, Grade> grade;

        Filters(CriteriaBuilder cb, AbstractQuery<?> query, Root<Product> root) {
            this.cb = cb;
            this.query = query;
            this.root = root;
        }

        Join<Product, Species> species() {
            if (species == null) {
                species = root.join("species", JoinType.LEFT);
            }
            return species;
        }

        Join<Product, Grade> grade() {
            if (grade == null) {
                grade = root.join("grade", JoinType.LEFT);
            }
            return grade;
        }

        /** All filters of {@code q}, except the dimension {@code exclude} (may be null). */
        Predicate build(ProductQuery q, Dimension exclude) {
            List<Predicate> predicates = new ArrayList<>();

            // Visibility: active products of species that may be traded.
            predicates.add(cb.equal(root.get("status"), ProductStatus.ACTIVE));
            predicates.add(cb.or(cb.isNull(species().get("id")), cb.isFalse(species().get("tradeRestricted"))));

            if (q.categoryIds() != null) {
                predicates.add(q.categoryIds().isEmpty()
                        ? cb.disjunction()
                        : root.get("category").get("id").in(q.categoryIds()));
            }
            if (q.text() != null && !q.text().isBlank()) {
                String pattern = "%" + escapeLike(q.text().strip().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(species().get("scientificName")), pattern, '\\'),
                        cb.like(cb.lower(species().get("commonName")), pattern, '\\')));
            }
            if (exclude != Dimension.SPECIES && !q.species().isEmpty()) {
                predicates.add(species().get("slug").in(q.species()));
            }
            if (exclude != Dimension.GRADE && !q.grades().isEmpty()) {
                predicates.add(grade().get("slug").in(q.grades()));
            }
            if (exclude != Dimension.COLOUR && !q.colours().isEmpty()) {
                Subquery<UUID> withColour = query.subquery(UUID.class);
                Root<Product> other = withColour.from(Product.class);
                Join<Product, Colour> colour = other.join("colours");
                withColour.select(other.get("id"))
                        .where(cb.equal(other.get("id"), root.get("id")), colour.get("slug").in(q.colours()));
                predicates.add(cb.exists(withColour));
            }
            if (exclude != Dimension.PRICE) {
                if (q.minPriceCents() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("minPriceCents"), q.minPriceCents()));
                }
                if (q.maxPriceCents() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("minPriceCents"), q.maxPriceCents()));
                }
            }
            if (exclude != Dimension.AVAILABILITY) {
                switch (q.availability()) {
                    case IN_STOCK -> predicates.add(cb.exists(purchasableVariant()));
                    case COMING_SOON -> predicates.add(cb.and(
                            cb.not(cb.exists(purchasableVariant())), cb.isNotNull(root.get("availableFrom"))));
                    case ALL -> { /* no filter */ }
                }
            }
            if (q.featuredOnly()) {
                predicates.add(cb.isTrue(root.get("featured")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        }

        /** Mirrors {@link Product#isAvailable}: an active variant whose pack fits into the stock. */
        Subquery<UUID> purchasableVariant() {
            Subquery<UUID> sub = query.subquery(UUID.class);
            Root<ProductVariant> variant = sub.from(ProductVariant.class);
            sub.select(variant.get("id")).where(
                    cb.equal(variant.get("product").get("id"), root.get("id")),
                    cb.isTrue(variant.get("active")),
                    cb.lessThanOrEqualTo(variant.get("packSize"), root.<Integer>get("stockUnits")));
            return sub;
        }

        private static String escapeLike(String value) {
            return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        }
    }
}
