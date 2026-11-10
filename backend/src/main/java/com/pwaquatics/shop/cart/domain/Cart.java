package com.pwaquatics.shop.cart.domain;

import com.pwaquatics.shop.common.persistence.AbstractEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A shopping cart, owned either by a customer ({@code userId}) or by a guest browser
 * (hash of the cart cookie). Stock is not reserved by the cart; that happens when an
 * order is placed.
 */
@Entity
@Table(name = "carts")
public class Cart extends AbstractEntity {

    /** Guest carts are kept this long after their last change. */
    public static final Duration GUEST_LIFETIME = Duration.ofDays(30);

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "anonymous_token_hash", length = 64, updatable = false)
    private String anonymousTokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private List<CartItem> items = new ArrayList<>();

    protected Cart() {}

    public static Cart forGuest(String tokenHash, Instant now) {
        Cart cart = new Cart();
        cart.anonymousTokenHash = tokenHash;
        cart.createdAt = now;
        cart.touch(now);
        return cart;
    }

    public Optional<CartItem> item(UUID itemId) {
        return items.stream().filter(item -> item.getId().equals(itemId)).findFirst();
    }

    public Optional<CartItem> itemForVariant(UUID variantId) {
        return items.stream().filter(item -> item.getVariantId().equals(variantId)).findFirst();
    }

    /** Puts {@code quantity} packs of a variant into the cart, replacing an existing line's quantity. */
    public CartItem put(UUID variantId, int quantity, Instant now) {
        CartItem item = itemForVariant(variantId).orElse(null);
        if (item == null) {
            item = new CartItem(this, variantId, quantity, now);
            items.add(item);
        } else {
            item.changeQuantity(quantity, now);
        }
        touch(now);
        return item;
    }

    public void changeQuantity(CartItem item, int quantity, Instant now) {
        item.changeQuantity(quantity, now);
        touch(now);
    }

    public void remove(CartItem item, Instant now) {
        items.remove(item);
        touch(now);
    }

    public void clear(Instant now) {
        items.clear();
        touch(now);
    }

    /** Marks the cart as changed; guest carts get a fresh expiry. */
    private void touch(Instant now) {
        updatedAt = now;
        if (userId == null) {
            expiresAt = now.plus(GUEST_LIFETIME);
        }
    }

    public UUID getUserId() {
        return userId;
    }

    public List<CartItem> getItems() {
        return items;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
