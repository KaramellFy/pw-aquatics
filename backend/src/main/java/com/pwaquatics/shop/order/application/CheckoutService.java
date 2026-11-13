package com.pwaquatics.shop.order.application;

import com.pwaquatics.shop.audit.application.AuditService;
import com.pwaquatics.shop.cart.application.CartCheckout;
import com.pwaquatics.shop.cart.application.CartLine;
import com.pwaquatics.shop.catalog.application.CatalogLookup;
import com.pwaquatics.shop.catalog.application.CatalogStock;
import com.pwaquatics.shop.catalog.application.view.VariantSnapshot;
import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.common.error.ConflictException;
import com.pwaquatics.shop.common.error.NotFoundException;
import com.pwaquatics.shop.common.error.RateLimitedException;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.customer.application.AddressService;
import com.pwaquatics.shop.customer.application.view.AddressView;
import com.pwaquatics.shop.identity.application.AccountService;
import com.pwaquatics.shop.identity.application.view.ProfileView;
import com.pwaquatics.shop.inventory.application.InventoryService;
import com.pwaquatics.shop.notification.application.OutboxService;
import com.pwaquatics.shop.order.application.view.OrderTotalsView;
import com.pwaquatics.shop.order.application.view.PlacedOrderView;
import com.pwaquatics.shop.order.application.view.QuoteLineView;
import com.pwaquatics.shop.order.application.view.QuoteView;
import com.pwaquatics.shop.order.domain.NewOrder;
import com.pwaquatics.shop.order.domain.NewOrderItem;
import com.pwaquatics.shop.order.domain.Order;
import com.pwaquatics.shop.order.domain.OrderAddress;
import com.pwaquatics.shop.order.domain.OrderNumber;
import com.pwaquatics.shop.order.infrastructure.OrderRepository;
import com.pwaquatics.shop.payment.application.BankTransferPayments;
import com.pwaquatics.shop.payment.application.StripePayments;
import com.pwaquatics.shop.payment.domain.PaymentMethod;
import com.pwaquatics.shop.pricing.domain.PriceCalculator;
import com.pwaquatics.shop.pricing.domain.PricedLine;
import com.pwaquatics.shop.pricing.domain.Totals;
import com.pwaquatics.shop.security.ratelimit.RateLimiter;
import com.pwaquatics.shop.shipping.application.ShippingService;
import com.pwaquatics.shop.shipping.application.view.ShippingOption;
import com.pwaquatics.shop.shipping.application.view.ShippingOptions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checkout: shipping options, the price quote and placing the order (docs/API.md §2.5).
 *
 * <p>Nothing here trusts the client. A request names the customer's own address and a
 * shipping method; the items come from the server-side cart, and prices, tax rates, the
 * shipping price and availability from the database.
 */
@Service
public class CheckoutService {

    private static final String RATE_LIMIT = "checkout-order";

    private final CartCheckout cart;
    private final CatalogLookup catalog;
    private final CatalogStock stock;
    private final InventoryService inventory;
    private final AddressService addresses;
    private final ShippingService shipping;
    private final BankTransferPayments bankTransfer;
    private final StripePayments stripe;
    private final AccountService accounts;
    private final OrderRepository orders;
    private final OrderViews views;
    private final OrderEmails emails;
    private final OutboxService outbox;
    private final AuditService audit;
    private final RateLimiter rateLimiter;
    private final int bankTransferPaymentDays;
    private final Duration cardPaymentTime;
    private final Clock clock;

    CheckoutService(
            CartCheckout cart,
            CatalogLookup catalog,
            CatalogStock stock,
            InventoryService inventory,
            AddressService addresses,
            ShippingService shipping,
            BankTransferPayments bankTransfer,
            StripePayments stripe,
            AccountService accounts,
            OrderRepository orders,
            OrderViews views,
            OrderEmails emails,
            OutboxService outbox,
            AuditService audit,
            RateLimiter rateLimiter,
            AppProperties properties,
            Clock clock) {
        this.cart = cart;
        this.catalog = catalog;
        this.stock = stock;
        this.inventory = inventory;
        this.addresses = addresses;
        this.shipping = shipping;
        this.bankTransfer = bankTransfer;
        this.stripe = stripe;
        this.accounts = accounts;
        this.orders = orders;
        this.views = views;
        this.emails = emails;
        this.outbox = outbox;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.bankTransferPaymentDays = properties.orders().bankTransferPaymentDays();
        this.cardPaymentTime = Duration.ofMinutes(properties.orders().cardPaymentMinutes());
        this.clock = clock;
    }

    /**
     * Shipping methods for the customer's cart and a destination country.
     *
     * @throws ConflictException {@code CART_EMPTY} or {@code CART_CHANGED}
     */
    @Transactional
    public ShippingOptions shippingMethods(UUID userId, String countryCode) {
        Basket basket = basket(cart.lockLines(userId));
        return shipping.optionsFor(countryCode, basket.containsLiveAnimals(), basket.subtotalCents());
    }

    /**
     * The price breakdown for the cart, shipped to one of the customer's addresses.
     *
     * @throws NotFoundException the address is not one of the customer's own
     * @throws ConflictException {@code CART_EMPTY}, {@code CART_CHANGED}, {@code SHIPPING_PAUSED} or
     *     {@code SHIPPING_METHOD_UNAVAILABLE}
     */
    @Transactional
    public QuoteView quote(UUID userId, UUID shippingAddressId, String shippingMethodCode) {
        Basket basket = basket(cart.lockLines(userId));
        AddressView address = addresses.get(userId, shippingAddressId);
        ShippingOption method = shipping.require(
                shippingMethodCode, address.countryCode(), basket.containsLiveAnimals(), basket.subtotalCents());
        Totals totals = PriceCalculator.totals(basket.pricedLines(), method.price().amount());
        return new QuoteView(
                basket.lines().stream().map(CheckoutService::quoteLine).toList(),
                new OrderTotalsView(totals.subtotal(), totals.shipping(), totals.total(), totals.includedTax()),
                method,
                basket.containsLiveAnimals(),
                availablePaymentMethods());
    }

    /**
     * Places the order: one transaction that checks everything again, takes the animals out
     * of stock, stores the order with all snapshots, empties the cart and queues the
     * confirmation email. A card order is paid afterwards on the payment page
     * ({@link OrderPayments#startCardPayment}).
     *
     * <p>Repeating a request with the same {@code idempotencyKey} returns the order created
     * the first time instead of creating another one.
     *
     * @throws NotFoundException an address is not one of the customer's own
     * @throws ConflictException {@code CART_EMPTY}, {@code CART_CHANGED}, {@code INSUFFICIENT_STOCK},
     *     {@code SHIPPING_PAUSED}, {@code SHIPPING_METHOD_UNAVAILABLE}, {@code PAYMENT_METHOD_UNAVAILABLE} or
     *     {@code PRICE_CHANGED}
     * @throws BusinessRuleException {@code LIVE_ANIMAL_CONDITIONS_REQUIRED}
     * @throws RateLimitedException too many orders in a short time
     */
    @Transactional
    public PlacedOrderView placeOrder(UUID userId, PlaceOrder command, String idempotencyKey, String clientIp) {
        rateLimiter.consume(RATE_LIMIT, userId.toString());

        // The cart lock queues this customer's checkout requests one after the other.
        List<CartLine> cartLines = cart.lockLines(userId);
        Optional<Order> earlier = orders.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (earlier.isPresent()) {
            return views.placed(earlier.get());
        }
        if (cartLines.isEmpty()) {
            throw new ConflictException("CART_EMPTY", "Your cart is empty.");
        }

        // From here on prices and stock of these products cannot change until we commit.
        stock.lockProductsOf(cartLines.stream().map(CartLine::variantId).toList());
        Basket basket = basket(cartLines);

        AddressView shippingAddress = addresses.get(userId, command.shippingAddressId());
        AddressView billingAddress = command.billingAddressId() == null
                ? shippingAddress
                : addresses.get(userId, command.billingAddressId());
        ShippingOption method = shipping.require(
                command.shippingMethodCode(),
                shippingAddress.countryCode(),
                basket.containsLiveAnimals(),
                basket.subtotalCents());
        if (!availablePaymentMethods().contains(command.paymentMethod())) {
            throw new ConflictException("PAYMENT_METHOD_UNAVAILABLE", "This payment method is not available.");
        }
        if (basket.containsLiveAnimals() && !command.acceptedLiveAnimalConditions()) {
            throw new BusinessRuleException(
                    "LIVE_ANIMAL_CONDITIONS_REQUIRED", "Please accept the conditions for shipping live animals.");
        }

        Totals totals = PriceCalculator.totals(basket.pricedLines(), method.price().amount());
        if (!totals.total().equals(command.expectedTotal())) {
            throw new ConflictException(
                    "PRICE_CHANGED", "The total of your order has changed. Please check it before ordering.");
        }

        Instant now = clock.instant();
        ProfileView customer = accounts.profile(userId);
        LocalDate today = LocalDate.ofInstant(now, ShippingService.SHOP_ZONE);
        Order order = Order.place(
                new NewOrder(
                        OrderNumber.of(today.getYear(), orders.nextOrderSequence()),
                        userId,
                        customer.email(),
                        customer.locale(),
                        command.paymentMethod(),
                        basket.lines().stream().map(CheckoutService::orderItem).toList(),
                        method.price().amount(),
                        method.code(),
                        method.name(),
                        method.carrier(),
                        method.plannedDispatchDate(),
                        snapshot(shippingAddress),
                        snapshot(billingAddress),
                        command.customerNote(),
                        idempotencyKey,
                        paymentDueAt(command.paymentMethod(), now)),
                now);
        if (!order.total().equals(totals.total())) {
            throw new IllegalStateException("Order total differs from the quoted total");
        }
        orders.saveAndFlush(order);

        // Final guard against overselling: fails (and rolls everything back) if stock is short.
        inventory.reserveForOrder(order.getId(), order.stockUnitsByProduct());
        cart.clear(userId);
        outbox.enqueueEmail(emails.confirmation(order, views.paymentInstructions(order)));
        audit.record(userId, "ORDER_PLACED", "ORDER", order.getId(), Map.of("orderNumber", order.getOrderNumber()),
                clientIp);
        return views.placed(order);
    }

    // ------------------------------------------------------------------ helpers

    /** The cart lines with their current catalog data. */
    private record Basket(List<Line> lines) {

        record Line(CartLine cartLine, VariantSnapshot variant) {}

        List<PricedLine> pricedLines() {
            return lines.stream()
                    .map(line -> new PricedLine(
                            line.variant().unitPrice().amount(),
                            line.cartLine().quantity(),
                            line.variant().taxRateBps()))
                    .toList();
        }

        long subtotalCents() {
            return PriceCalculator.totals(pricedLines()).subtotal().amount();
        }

        boolean containsLiveAnimals() {
            return lines.stream().anyMatch(line -> line.variant().liveAnimal());
        }
    }

    /**
     * Pairs the cart lines with current catalog data and checks that every line can be bought
     * as it is. The checkout never changes quantities on its own: if anything no longer fits,
     * the customer is sent back to the cart, which shows what changed.
     */
    private Basket basket(List<CartLine> cartLines) {
        if (cartLines.isEmpty()) {
            throw new ConflictException("CART_EMPTY", "Your cart is empty.");
        }
        Map<UUID, VariantSnapshot> variants =
                catalog.variants(cartLines.stream().map(CartLine::variantId).toList());
        Map<UUID, Integer> unitsByProduct = new HashMap<>();
        Map<UUID, Integer> stockByProduct = new TreeMap<>();
        List<Basket.Line> lines = cartLines.stream()
                .map(cartLine -> {
                    VariantSnapshot variant = variants.get(cartLine.variantId());
                    if (variant == null || !variant.purchasable() || cartLine.quantity() > variant.maxQuantity()) {
                        throw cartChanged();
                    }
                    unitsByProduct.merge(variant.productId(), cartLine.quantity() * variant.packSize(), Math::addExact);
                    stockByProduct.put(variant.productId(), variant.stockUnits());
                    return new Basket.Line(cartLine, variant);
                })
                .toList();
        // All pack sizes of a product share the same animals.
        unitsByProduct.forEach((productId, units) -> {
            if (units > stockByProduct.get(productId)) {
                throw cartChanged();
            }
        });
        return new Basket(lines);
    }

    private static ConflictException cartChanged() {
        return new ConflictException(
                "CART_CHANGED", "Some items in your cart are no longer available as requested. Please review your cart.");
    }

    /** Card payment first (it is the quickest way to get the animals on their way), then bank transfer. */
    private List<PaymentMethod> availablePaymentMethods() {
        List<PaymentMethod> methods = new ArrayList<>(2);
        if (stripe.isAvailable()) {
            methods.add(PaymentMethod.STRIPE);
        }
        if (bankTransfer.isAvailable()) {
            methods.add(PaymentMethod.BANK_TRANSFER);
        }
        return List.copyOf(methods);
    }

    /**
     * How long the animals stay reserved for an unpaid order: {@code cardPaymentMinutes} for card
     * payment; for bank transfer, until the end of the day {@code bankTransferPaymentDays} days
     * after the order.
     */
    private Instant paymentDueAt(PaymentMethod method, Instant now) {
        if (method == PaymentMethod.STRIPE) {
            return now.plus(cardPaymentTime);
        }
        return LocalDate.ofInstant(now, ShippingService.SHOP_ZONE)
                .plusDays(bankTransferPaymentDays)
                .atTime(LocalTime.of(23, 59, 59))
                .atZone(ShippingService.SHOP_ZONE)
                .toInstant();
    }

    private static QuoteLineView quoteLine(Basket.Line line) {
        VariantSnapshot variant = line.variant();
        int quantity = line.cartLine().quantity();
        return new QuoteLineView(
                variant.variantId(),
                variant.productSlug(),
                variant.productName(),
                variant.variantName(),
                variant.speciesName(),
                variant.gradeName(),
                variant.categorySlug(),
                variant.colourHex(),
                variant.image(),
                quantity,
                variant.unitPrice(),
                Money.eur(new PricedLine(variant.unitPrice().amount(), quantity, variant.taxRateBps()).totalCents()));
    }

    private static NewOrderItem orderItem(Basket.Line line) {
        VariantSnapshot variant = line.variant();
        return new NewOrderItem(
                variant.productId(),
                variant.variantId(),
                variant.sku(),
                variant.productName(),
                variant.variantName(),
                variant.speciesName(),
                variant.gradeName(),
                variant.liveAnimal(),
                variant.packSize(),
                variant.unitPrice().amount(),
                line.cartLine().quantity(),
                variant.taxRateBps());
    }

    private static OrderAddress snapshot(AddressView address) {
        return new OrderAddress(
                address.firstName(),
                address.lastName(),
                address.company(),
                address.street(),
                address.houseNumber(),
                address.addressLine2(),
                address.postalCode(),
                address.city(),
                address.countryCode(),
                address.phone());
    }
}
