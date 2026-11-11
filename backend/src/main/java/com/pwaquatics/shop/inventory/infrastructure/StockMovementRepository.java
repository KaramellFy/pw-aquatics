package com.pwaquatics.shop.inventory.infrastructure;

import com.pwaquatics.shop.inventory.domain.StockMovement;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {}
