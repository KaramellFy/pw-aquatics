package com.pwaquatics.shop.inventory.application;

import com.pwaquatics.shop.catalog.application.CatalogStock;
import com.pwaquatics.shop.common.error.ConflictException;
import com.pwaquatics.shop.inventory.domain.StockMovement;
import com.pwaquatics.shop.inventory.domain.StockMovementReason;
import com.pwaquatics.shop.inventory.infrastructure.StockMovementRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock changes caused by orders. Each change updates the product's balance and writes a
 * ledger entry in the caller's transaction, so the ledger always adds up to the balance.
 *
 * <p>Stock is taken when an order is placed (not when it is paid) and returned when an
 * unshipped order is cancelled: a live animal is never sold twice (docs/ARCHITECTURE.md §5.4).
 */
@Service
public class InventoryService {

    private final CatalogStock stock;
    private final StockMovementRepository movements;
    private final Clock clock;

    InventoryService(CatalogStock stock, StockMovementRepository movements, Clock clock) {
        this.stock = stock;
        this.movements = movements;
        this.clock = clock;
    }

    /**
     * Takes the animals of a new order out of stock.
     *
     * @param unitsByProduct animals per product id
     * @throws ConflictException {@code INSUFFICIENT_STOCK}; the caller's transaction must roll back
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserveForOrder(UUID orderId, Map<UUID, Integer> unitsByProduct) {
        Instant now = clock.instant();
        stock.take(unitsByProduct).forEach((productId, stockAfter) -> movements.save(StockMovement.forOrder(
                productId, -unitsByProduct.get(productId), stockAfter, StockMovementReason.ORDER_PLACED, orderId,
                now)));
    }

    /** Returns the animals of a cancelled, unshipped order to stock. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseForOrder(UUID orderId, Map<UUID, Integer> unitsByProduct) {
        Instant now = clock.instant();
        stock.giveBack(unitsByProduct).forEach((productId, stockAfter) -> movements.save(StockMovement.forOrder(
                productId, unitsByProduct.get(productId), stockAfter, StockMovementReason.ORDER_CANCELLED, orderId,
                now)));
    }
}
