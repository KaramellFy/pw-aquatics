package com.pwaquatics.shop.catalog.infrastructure;

import com.pwaquatics.shop.catalog.domain.ProductVariant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    /** Variants with everything needed to price and display them. */
    @Query("""
            SELECT v FROM ProductVariant v
            JOIN FETCH v.product p
            LEFT JOIN FETCH p.species
            JOIN FETCH p.category
            LEFT JOIN FETCH p.grade
            WHERE v.id IN :ids
            """)
    List<ProductVariant> findAllForSale(@Param("ids") Collection<UUID> ids);
}
