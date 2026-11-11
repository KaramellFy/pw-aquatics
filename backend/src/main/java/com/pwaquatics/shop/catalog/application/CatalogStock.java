package com.pwaquatics.shop.catalog.application;

import com.pwaquatics.shop.catalog.infrastructure.ProductRepository;
import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.common.error.ConflictException;
import com.pwaquatics.shop.common.error.NotFoundException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changes the stock balance of products for other modules (inventory). Every method must be
 * called inside the caller's transaction, so stock and the order change together or not at all.
 *
 * <p>Stock is counted in animals. The balance is changed with conditional SQL updates, so it
 * can never become negative, no matter how many orders arrive at the same time
 * (docs/DATABASE.md §4).
 */
@Service
public class CatalogStock {

    private final ProductRepository products;

    CatalogStock(ProductRepository products) {
        this.products = products;
    }

    /**
     * Locks the products behind the given variants until the transaction ends. Call this
     * before reading prices and availability for an order, so they cannot change in between.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockProductsOf(Collection<UUID> variantIds) {
        if (!variantIds.isEmpty()) {
            products.lockIdsByVariantIds(variantIds);
        }
    }

    /**
     * Takes animals out of stock.
     *
     * @param unitsByProduct animals to take per product id (positive numbers)
     * @return the stock left per product, in product id order
     * @throws ConflictException {@code INSUFFICIENT_STOCK} if any product has fewer animals left
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<UUID, Integer> take(Map<UUID, Integer> unitsByProduct) {
        Map<UUID, Integer> stockAfter = new LinkedHashMap<>();
        // Always in id order: the same lock order as lockProductsOf.
        new TreeMap<>(unitsByProduct).forEach((productId, units) -> {
            requirePositive(units);
            if (products.takeStock(productId, units) == 0) {
                throw new ConflictException(
                        "INSUFFICIENT_STOCK", "Some items are no longer in stock in the requested quantity.");
            }
            stockAfter.put(productId, products.stockOf(productId));
        });
        return stockAfter;
    }

    /**
     * Puts animals back into stock (cancelled order). Products that no longer exist are skipped.
     *
     * @return the new stock per product that was changed, in product id order
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<UUID, Integer> giveBack(Map<UUID, Integer> unitsByProduct) {
        Map<UUID, Integer> stockAfter = new LinkedHashMap<>();
        new TreeMap<>(unitsByProduct).forEach((productId, units) -> {
            requirePositive(units);
            if (products.returnStock(productId, units) == 1) {
                stockAfter.put(productId, products.stockOf(productId));
            }
        });
        return stockAfter;
    }

    /**
     * Locks one product until the transaction ends and returns its stock (for a stocktake).
     *
     * @throws NotFoundException if the product does not exist
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int lockStock(UUID productId) {
        Integer stock = products.lockStockOf(productId);
        if (stock == null) {
            throw new NotFoundException("Product not found.");
        }
        return stock;
    }

    /**
     * Changes the stock of one product by {@code delta} animals (manual corrections).
     *
     * @return the stock afterwards
     * @throws NotFoundException if the product does not exist
     * @throws BusinessRuleException {@code STOCK_NEGATIVE} if fewer animals are in stock than removed
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int change(UUID productId, int delta) {
        if (delta == 0) {
            throw new IllegalArgumentException("A stock change must not be zero");
        }
        if (products.changeStock(productId, delta) == 0) {
            if (products.stockOf(productId) == null) {
                throw new NotFoundException("Product not found.");
            }
            throw new BusinessRuleException("STOCK_NEGATIVE", "There are fewer animals in stock than you want to remove.");
        }
        return products.stockOf(productId);
    }

    /** Whether the product exists. */
    @Transactional(readOnly = true)
    public boolean exists(UUID productId) {
        return products.existsById(productId);
    }

    private static void requirePositive(int units) {
        if (units <= 0) {
            throw new IllegalArgumentException("Stock changes must be positive");
        }
    }
}
