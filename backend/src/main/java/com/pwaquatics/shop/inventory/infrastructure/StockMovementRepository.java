package com.pwaquatics.shop.inventory.infrastructure;

import com.pwaquatics.shop.inventory.domain.StockMovement;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    /** A product's ledger, newest first. */
    @Query("SELECT m FROM StockMovement m WHERE m.productId = :productId ORDER BY m.createdAt DESC, m.id DESC")
    Page<StockMovement> findByProduct(@Param("productId") UUID productId, Pageable pageable);
}
