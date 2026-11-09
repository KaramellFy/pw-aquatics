package com.pwaquatics.shop.catalog.infrastructure;

import com.pwaquatics.shop.catalog.domain.Product;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    String VISIBLE = "p.status = com.pwaquatics.shop.catalog.domain.ProductStatus.ACTIVE "
            + "AND (s IS NULL OR s.tradeRestricted = false)";

    @Query("""
            SELECT p FROM Product p
            LEFT JOIN FETCH p.species s
            JOIN FETCH p.category
            LEFT JOIN FETCH p.grade
            WHERE p.slug = :slug AND\s""" + VISIBLE)
    Optional<Product> findVisibleBySlug(@Param("slug") String slug);

    /** Visible products of the same species (or, as a fallback, category), best first. */
    @Query("""
            SELECT p.id FROM Product p
            LEFT JOIN p.species s
            WHERE p.id <> :excludeId AND (s.id = :speciesId OR p.category.id = :categoryId) AND\s""" + VISIBLE + """
             ORDER BY CASE WHEN s.id = :speciesId THEN 0 ELSE 1 END,
                      CASE WHEN EXISTS (SELECT v.id FROM ProductVariant v
                                        WHERE v.product = p AND v.active = true AND v.packSize <= p.stockUnits)
                           THEN 0 ELSE 1 END,
                      p.featured DESC, p.publishedAt DESC, p.id
            """)
    List<UUID> findRelatedIds(
            @Param("excludeId") UUID excludeId,
            @Param("speciesId") UUID speciesId,
            @Param("categoryId") UUID categoryId,
            Limit limit);
}
