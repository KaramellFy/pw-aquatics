package com.pwaquatics.shop.inventory.api;

import static com.pwaquatics.shop.common.text.Texts.trimToNull;

import com.pwaquatics.shop.audit.application.Actor;
import com.pwaquatics.shop.common.api.PageResponse;
import com.pwaquatics.shop.inventory.application.AdminInventoryService;
import com.pwaquatics.shop.inventory.application.view.StockLevelView;
import com.pwaquatics.shop.inventory.application.view.StockMovementView;
import com.pwaquatics.shop.inventory.domain.StockMovementReason;
import com.pwaquatics.shop.security.ShopUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stock corrections and history (docs/API.md §3.4). Role ADMIN only. The stock overview is
 * the product list with its stock filter ({@code GET /admin/products?stock=LOW_STOCK}).
 */
@RestController
@RequestMapping("/api/v1/admin/inventory")
@Tag(name = "Admin: inventory")
class AdminInventoryController {

    private final AdminInventoryService inventory;

    AdminInventoryController(AdminInventoryService inventory) {
        this.inventory = inventory;
    }

    /**
     * @param delta animals added (positive) or removed (negative)
     * @param reason RESTOCK, RETURN (positive), LOSS (negative) or MANUAL_ADJUSTMENT
     */
    record AdjustmentRequest(
            @Min(-100_000) @Max(100_000) int delta,
            @NotNull StockMovementReason reason,
            @Size(max = 500) String note) {}

    record CountRequest(@Min(0) @Max(1_000_000) int countedUnits, @Size(max = 450) String note) {}

    @Operation(summary = "Add or remove animals (restock, loss, correction, return)")
    @PostMapping("/{productId}/adjustments")
    StockLevelView adjust(
            @PathVariable UUID productId,
            @Valid @RequestBody AdjustmentRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return inventory.adjust(productId, request.delta(), request.reason(), trimToNull(request.note()),
                new Actor(principal.userId(), http.getRemoteAddr()));
    }

    @Operation(summary = "Stocktake: the counted number becomes the stock")
    @PutMapping("/{productId}/count")
    StockLevelView count(
            @PathVariable UUID productId,
            @Valid @RequestBody CountRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return inventory.count(productId, request.countedUnits(), trimToNull(request.note()),
                new Actor(principal.userId(), http.getRemoteAddr()));
    }

    @Operation(summary = "Stock history of a product, newest first")
    @GetMapping("/{productId}/movements")
    PageResponse<StockMovementView> movements(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return inventory.movements(productId, page, size);
    }
}
