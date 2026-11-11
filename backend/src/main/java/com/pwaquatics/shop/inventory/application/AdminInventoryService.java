package com.pwaquatics.shop.inventory.application;

import com.pwaquatics.shop.audit.application.Actor;
import com.pwaquatics.shop.audit.application.AuditService;
import com.pwaquatics.shop.catalog.application.CatalogStock;
import com.pwaquatics.shop.common.api.PageResponse;
import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.common.error.NotFoundException;
import com.pwaquatics.shop.inventory.application.view.StockLevelView;
import com.pwaquatics.shop.inventory.application.view.StockMovementView;
import com.pwaquatics.shop.inventory.domain.StockMovement;
import com.pwaquatics.shop.inventory.domain.StockMovementReason;
import com.pwaquatics.shop.inventory.infrastructure.StockMovementRepository;
import java.time.Clock;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manual stock changes by the shop owner (docs/API.md §3.4): restock, losses, corrections,
 * returns and stocktakes. Each change updates the balance with a conditional SQL update and
 * writes a ledger entry and an audit record in the same transaction.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminInventoryService {

    /** Reasons a person may give; order-related ones are written by the order module only. */
    public static final Set<StockMovementReason> MANUAL_REASONS = EnumSet.of(
            StockMovementReason.RESTOCK, StockMovementReason.LOSS, StockMovementReason.MANUAL_ADJUSTMENT,
            StockMovementReason.RETURN);

    private final CatalogStock stock;
    private final StockMovementRepository movements;
    private final AuditService audit;
    private final Clock clock;

    AdminInventoryService(CatalogStock stock, StockMovementRepository movements, AuditService audit, Clock clock) {
        this.stock = stock;
        this.movements = movements;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Adds or removes animals. Restocks and returns add (positive delta), losses remove
     * (negative delta), corrections may go either way.
     *
     * @throws BusinessRuleException {@code ADJUSTMENT_INVALID} for a reason/sign mismatch,
     *     {@code STOCK_NEGATIVE} when removing more than is in stock
     */
    @Transactional
    public StockLevelView adjust(UUID productId, int delta, StockMovementReason reason, String note, Actor actor) {
        boolean signMatches = switch (reason) {
            case RESTOCK, RETURN -> delta > 0;
            case LOSS -> delta < 0;
            case MANUAL_ADJUSTMENT -> delta != 0;
            default -> false;
        };
        if (!MANUAL_REASONS.contains(reason) || !signMatches) {
            throw new BusinessRuleException("ADJUSTMENT_INVALID",
                    "Restocks and returns add animals, losses remove them, and a change cannot be zero.");
        }
        int after = stock.change(productId, delta);
        movements.save(StockMovement.manual(productId, delta, after, reason, note, actor.userId(), clock.instant()));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("delta", delta);
        details.put("reason", reason.name());
        details.put("stockAfter", after);
        audit.record(actor, "STOCK_ADJUSTED", "PRODUCT", productId, details);
        return new StockLevelView(productId, after);
    }

    /**
     * Records a stocktake: the counted number becomes the stock; the difference is written to
     * the ledger as a correction. Counting the same number as stored changes nothing.
     */
    @Transactional
    public StockLevelView count(UUID productId, int countedUnits, String note, Actor actor) {
        if (countedUnits < 0) {
            throw new BusinessRuleException("ADJUSTMENT_INVALID", "The counted number cannot be negative.");
        }
        int before = stock.lockStock(productId);
        int delta = countedUnits - before;
        if (delta != 0) {
            int after = stock.change(productId, delta);
            String text = note == null ? "Stocktake" : "Stocktake: " + note;
            movements.save(StockMovement.manual(productId, delta, after, StockMovementReason.MANUAL_ADJUSTMENT,
                    text.length() > 500 ? text.substring(0, 500) : text, actor.userId(), clock.instant()));
        }
        audit.record(actor, "STOCK_COUNTED", "PRODUCT", productId,
                Map.of("counted", countedUnits, "previous", before));
        return new StockLevelView(productId, countedUnits);
    }

    /** A product's stock history, newest first. */
    @Transactional(readOnly = true)
    public PageResponse<StockMovementView> movements(UUID productId, int page, int size) {
        if (!stock.exists(productId)) {
            throw new NotFoundException("Product not found.");
        }
        return PageResponse.of(movements.findByProduct(productId, PageRequest.of(page, size))
                .map(m -> new StockMovementView(m.getId(), m.getDelta(), m.getStockAfter(), m.getReason(), m.getNote(),
                        m.getOrderId(), m.getCreatedBy() != null, m.getCreatedAt())));
    }
}
