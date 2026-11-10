package com.pwaquatics.shop.cart.application;

import com.pwaquatics.shop.cart.infrastructure.CartRepository;
import com.pwaquatics.shop.common.ids.Ids;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The customer's cart as the checkout needs it: its raw lines, locked for the duration of
 * the caller's transaction, and a way to empty it once the order exists.
 *
 * <p>The row lock makes the cart the queue for one customer's checkout requests: a double
 * click or two browser tabs are processed one after the other, never at the same time.
 */
@Service
public class CartCheckout {

    private final CartRepository carts;
    private final Clock clock;

    CartCheckout(CartRepository carts, Clock clock) {
        this.carts = carts;
        this.clock = clock;
    }

    /**
     * Locks the customer's cart (creating an empty one if needed) and returns its lines in
     * the order they were added.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<CartLine> lockLines(UUID userId) {
        carts.insertForUserIfAbsent(Ids.newId(), userId, clock.instant());
        return carts.lockByUserId(userId).orElseThrow().getItems().stream()
                .map(item -> new CartLine(item.getVariantId(), item.getQuantity()))
                .toList();
    }

    /** Empties the customer's cart after the order has been created (same transaction). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void clear(UUID userId) {
        carts.lockByUserId(userId).ifPresent(cart -> cart.clear(clock.instant()));
    }
}
