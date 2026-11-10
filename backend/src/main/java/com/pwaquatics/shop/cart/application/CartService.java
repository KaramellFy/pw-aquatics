package com.pwaquatics.shop.cart.application;

import com.pwaquatics.shop.cart.application.view.CartLineView;
import com.pwaquatics.shop.cart.application.view.CartTotalsView;
import com.pwaquatics.shop.cart.application.view.CartView;
import com.pwaquatics.shop.cart.application.view.CartWarningView;
import com.pwaquatics.shop.cart.domain.Cart;
import com.pwaquatics.shop.cart.domain.CartItem;
import com.pwaquatics.shop.cart.infrastructure.CartRepository;
import com.pwaquatics.shop.catalog.application.CatalogLookup;
import com.pwaquatics.shop.catalog.application.view.VariantSnapshot;
import com.pwaquatics.shop.common.crypto.Tokens;
import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.common.error.ConflictException;
import com.pwaquatics.shop.common.error.NotFoundException;
import com.pwaquatics.shop.common.ids.Ids;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.identity.domain.UserAnonymised;
import com.pwaquatics.shop.pricing.domain.PriceCalculator;
import com.pwaquatics.shop.pricing.domain.PricedLine;
import com.pwaquatics.shop.pricing.domain.Totals;
import com.pwaquatics.shop.security.ratelimit.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The shopping cart for guests and customers.
 *
 * <p>The cart stores variant ids and quantities only. Prices, totals and availability are
 * taken from the catalog on every call, so nothing a client sends can change a price, and
 * the cart is always shown as it would be charged right now.
 *
 * <p>When a request carries both a login and a guest cart cookie (the visitor has just
 * signed in), the guest cart is merged into the customer's cart.
 */
@Service
public class CartService {

    /** How long the guest cart cookie should live (same as the cart itself). */
    public static final Duration GUEST_CART_LIFETIME = Cart.GUEST_LIFETIME;

    /** Upper bound for different products in one cart. */
    static final int MAX_LINES = 50;
    /** Hard cap for a merged line whose variant is currently unavailable. */
    private static final int MAX_QUANTITY = 20;
    private static final String RATE_LIMIT = "cart-write";

    private final CartRepository carts;
    private final CatalogLookup catalog;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    CartService(CartRepository carts, CatalogLookup catalog, RateLimiter rateLimiter, Clock clock) {
        this.carts = carts;
        this.catalog = catalog;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /** The current cart. Quantities above the available stock are lowered and reported as warnings. */
    @Transactional
    public CartResult view(CartOwner owner) {
        Resolved resolved = resolve(owner, false);
        return result(resolved);
    }

    /**
     * Adds packs of a variant (on top of what is already in the cart).
     *
     * @throws NotFoundException unknown variant
     * @throws ConflictException {@code PRODUCT_UNAVAILABLE} or {@code INSUFFICIENT_STOCK}
     * @throws BusinessRuleException {@code CART_FULL}
     */
    @Transactional
    public CartResult add(CartOwner owner, UUID variantId, int quantity, String clientIp) {
        rateLimiter.consume(RATE_LIMIT, clientIp);
        VariantSnapshot variant = catalog.variants(List.of(variantId)).get(variantId);
        if (variant == null) {
            throw new NotFoundException("Product not found.");
        }
        requirePurchasable(variant);

        Resolved resolved = resolve(owner, true);
        Cart cart = resolved.cart();
        CartItem existing = cart.itemForVariant(variantId).orElse(null);
        if (existing == null && cart.getItems().size() >= MAX_LINES) {
            throw new BusinessRuleException("CART_FULL", "Your cart cannot hold more different products.");
        }
        int newQuantity = (existing == null ? 0 : existing.getQuantity()) + quantity;
        requireInStock(variant, newQuantity);
        cart.put(variantId, newQuantity, clock.instant());
        carts.save(cart);
        return result(resolved);
    }

    /** Sets the quantity of a line in the caller's own cart. */
    @Transactional
    public CartResult changeQuantity(CartOwner owner, UUID itemId, int quantity, String clientIp) {
        rateLimiter.consume(RATE_LIMIT, clientIp);
        Resolved resolved = resolve(owner, false);
        CartItem item = ownItem(resolved.cart(), itemId);
        VariantSnapshot variant = catalog.variants(List.of(item.getVariantId())).get(item.getVariantId());
        if (variant == null) {
            throw new NotFoundException("Cart item not found.");
        }
        requirePurchasable(variant);
        requireInStock(variant, quantity);
        resolved.cart().changeQuantity(item, quantity, clock.instant());
        return result(resolved);
    }

    @Transactional
    public CartResult remove(CartOwner owner, UUID itemId, String clientIp) {
        rateLimiter.consume(RATE_LIMIT, clientIp);
        Resolved resolved = resolve(owner, false);
        resolved.cart().remove(ownItem(resolved.cart(), itemId), clock.instant());
        return result(resolved);
    }

    @Transactional
    public CartResult clear(CartOwner owner, String clientIp) {
        rateLimiter.consume(RATE_LIMIT, clientIp);
        Resolved resolved = resolve(owner, false);
        if (resolved.cart() != null) {
            resolved.cart().clear(clock.instant());
        }
        return result(resolved);
    }

    /** Removes the cart of a deleted account (same transaction as the anonymisation). */
    @EventListener
    @Transactional
    public void on(UserAnonymised event) {
        carts.deleteByUserId(event.userId());
    }

    // ------------------------------------------------------------------ cart resolution

    private record Resolved(Cart cart, String issuedGuestToken, boolean guestTokenConsumed) {}

    /**
     * Finds (and locks) the caller's cart. With {@code create} a missing cart is created.
     * A customer's leftover guest cart is merged first.
     */
    private Resolved resolve(CartOwner owner, boolean create) {
        Instant now = clock.instant();
        if (!owner.isCustomer()) {
            Cart guestCart = owner.hasGuestToken()
                    ? carts.lockByTokenHash(Tokens.sha256Hex(owner.guestToken())).orElse(null)
                    : null;
            if (guestCart != null) {
                return new Resolved(guestCart, null, false);
            }
            if (!create) {
                // An unknown cookie (expired cart) is cleaned up.
                return new Resolved(null, null, owner.hasGuestToken());
            }
            String token = Tokens.newToken();
            return new Resolved(Cart.forGuest(Tokens.sha256Hex(token), now), token, false);
        }

        // Lock order is always guest cart, then customer cart.
        Cart guestCart = owner.hasGuestToken()
                ? carts.lockByTokenHash(Tokens.sha256Hex(owner.guestToken())).orElse(null)
                : null;
        boolean needsCart = create || (guestCart != null && !guestCart.getItems().isEmpty());
        if (needsCart) {
            carts.insertForUserIfAbsent(Ids.newId(), owner.userId(), now);
        }
        Cart userCart = carts.lockByUserId(owner.userId()).orElse(null);
        if (guestCart != null) {
            if (userCart != null) {
                merge(guestCart, userCart, now);
            }
            carts.delete(guestCart);
        }
        return new Resolved(userCart, null, owner.hasGuestToken());
    }

    /** Adds the guest cart's lines to the customer's cart; quantities are summed, capped at the available stock. */
    private void merge(Cart from, Cart into, Instant now) {
        List<UUID> variantIds = from.getItems().stream().map(CartItem::getVariantId).toList();
        Map<UUID, VariantSnapshot> variants = catalog.variants(variantIds);
        for (CartItem guestItem : from.getItems()) {
            CartItem existing = into.itemForVariant(guestItem.getVariantId()).orElse(null);
            if (existing == null && into.getItems().size() >= MAX_LINES) {
                continue;
            }
            int sum = (existing == null ? 0 : existing.getQuantity()) + guestItem.getQuantity();
            VariantSnapshot variant = variants.get(guestItem.getVariantId());
            int cap = variant != null && variant.maxQuantity() > 0 ? variant.maxQuantity() : MAX_QUANTITY;
            into.put(guestItem.getVariantId(), Math.min(sum, cap), now);
        }
    }

    /** An item of someone else's cart is simply "not found". */
    private static CartItem ownItem(Cart cart, UUID itemId) {
        if (cart == null) {
            throw new NotFoundException("Cart item not found.");
        }
        return cart.item(itemId).orElseThrow(() -> new NotFoundException("Cart item not found."));
    }

    private static void requirePurchasable(VariantSnapshot variant) {
        if (!variant.purchasable()) {
            throw new ConflictException("PRODUCT_UNAVAILABLE", "This product is currently not available.");
        }
    }

    private static void requireInStock(VariantSnapshot variant, int quantity) {
        if (quantity > variant.maxQuantity()) {
            throw new ConflictException(
                    "INSUFFICIENT_STOCK",
                    "Only " + variant.maxQuantity() + " of this item can be ordered at the moment.");
        }
    }

    // ------------------------------------------------------------------ view

    private CartResult result(Resolved resolved) {
        return new CartResult(toView(resolved.cart()), resolved.issuedGuestToken(), resolved.guestTokenConsumed());
    }

    /** Builds the cart from current catalog data, lowering quantities that exceed the stock. */
    private CartView toView(Cart cart) {
        if (cart == null || cart.getItems().isEmpty()) {
            Totals empty = PriceCalculator.totals(List.of());
            return new CartView(List.of(), totalsView(empty), 0, List.of());
        }
        Instant now = clock.instant();
        Map<UUID, VariantSnapshot> variants =
                catalog.variants(cart.getItems().stream().map(CartItem::getVariantId).toList());

        List<CartLineView> lines = new ArrayList<>();
        List<CartWarningView> warnings = new ArrayList<>();
        List<PricedLine> priced = new ArrayList<>();
        int itemCount = 0;

        for (CartItem item : List.copyOf(cart.getItems())) {
            VariantSnapshot variant = variants.get(item.getVariantId());
            if (variant == null) {
                cart.remove(item, now);
                warnings.add(new CartWarningView("ITEM_REMOVED", null, null, null));
                continue;
            }
            boolean available = variant.purchasable();
            if (!available) {
                warnings.add(new CartWarningView("ITEM_UNAVAILABLE", item.getId(), variant.productName(), null));
            } else if (item.getQuantity() > variant.maxQuantity()) {
                cart.changeQuantity(item, variant.maxQuantity(), now);
                warnings.add(new CartWarningView(
                        "QUANTITY_REDUCED", item.getId(), variant.productName(), variant.maxQuantity()));
            }
            PricedLine line = new PricedLine(variant.unitPrice().amount(), item.getQuantity(), variant.taxRateBps());
            if (available) {
                priced.add(line);
                itemCount += item.getQuantity();
            }
            lines.add(new CartLineView(
                    item.getId(),
                    variant.variantId(),
                    variant.productSlug(),
                    variant.productName(),
                    variant.variantName(),
                    variant.speciesName(),
                    variant.gradeName(),
                    variant.categorySlug(),
                    variant.colourHex(),
                    variant.image(),
                    item.getQuantity(),
                    variant.maxQuantity(),
                    variant.unitPrice(),
                    Money.eur(line.totalCents()),
                    available));
        }
        return new CartView(lines, totalsView(PriceCalculator.totals(priced)), itemCount, warnings);
    }

    private static CartTotalsView totalsView(Totals totals) {
        return new CartTotalsView(totals.subtotal(), totals.total(), totals.includedTax());
    }
}
