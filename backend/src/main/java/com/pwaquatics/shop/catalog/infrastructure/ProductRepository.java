package com.pwaquatics.shop.catalog.infrastructure;

import com.pwaquatics.shop.catalog.domain.Product;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // ---------------------------------------------------------------- stock (order placement)

    /**
     * Locks the products behind the given variants until the transaction ends, always in id
     * order so that two orders with the same products can never deadlock.
     */
    @Query(value = """
            SELECT p.id FROM products p
            WHERE p.id IN (SELECT v.product_id FROM product_variants v WHERE v.id IN (:variantIds))
            ORDER BY p.id
            FOR UPDATE OF p
            """, nativeQuery = true)
    List<UUID> lockIdsByVariantIds(@Param("variantIds") Collection<UUID> variantIds);

    /** Takes animals out of stock, but only if enough are there. Returns the number of rows changed (0 or 1). */
    @Modifying
    @Query(value = """
            UPDATE products
            SET stock_units = stock_units - :units, updated_at = now(), version = version + 1
            WHERE id = :id AND stock_units >= :units
            """, nativeQuery = true)
    int takeStock(@Param("id") UUID id, @Param("units") int units);

    @Modifying
    @Query(value = """
            UPDATE products
            SET stock_units = stock_units + :units, updated_at = now(), version = version + 1
            WHERE id = :id
            """, nativeQuery = true)
    int returnStock(@Param("id") UUID id, @Param("units") int units);

    @Query(value = "SELECT stock_units FROM products WHERE id = :id", nativeQuery = true)
    Integer stockOf(@Param("id") UUID id);
}
