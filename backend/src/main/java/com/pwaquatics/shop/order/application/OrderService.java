package com.pwaquatics.shop.order.application;

import com.pwaquatics.shop.audit.application.AuditService;
import com.pwaquatics.shop.common.api.PageResponse;
import com.pwaquatics.shop.common.error.ConflictException;
import com.pwaquatics.shop.common.error.NotFoundException;
import com.pwaquatics.shop.order.application.view.OrderDetailView;
import com.pwaquatics.shop.order.application.view.OrderSummaryView;
import com.pwaquatics.shop.order.domain.Order;
import com.pwaquatics.shop.order.infrastructure.OrderRepository;
import com.pwaquatics.shop.security.ratelimit.RateLimiter;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A customer's own orders. Every operation takes the owner's id; an order of another
 * customer is reported as "not found", so its existence is never revealed.
 */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final OrderViews views;
    private final OrderCancellation cancellation;
    private final AuditService audit;
    private final RateLimiter rateLimiter;

    OrderService(
            OrderRepository orders,
            OrderViews views,
            OrderCancellation cancellation,
            AuditService audit,
            RateLimiter rateLimiter) {
        this.orders = orders;
        this.views = views;
        this.cancellation = cancellation;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
    }

    /** Order history, newest first. */
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryView> list(UUID userId, int page, int size) {
        return PageResponse.of(orders.findAllOf(userId, PageRequest.of(page, size)).map(views::summary));
    }

    @Transactional(readOnly = true)
    public OrderDetailView detail(UUID userId, String orderNumber) {
        return views.detail(orders.findByUserIdAndOrderNumber(userId, orderNumber).orElseThrow(OrderService::notFound));
    }

    /**
     * Cancels an order the customer has not paid yet and returns its animals to stock.
     *
     * @throws ConflictException {@code ORDER_NOT_CANCELLABLE} once the order is paid, shipped or already cancelled
     */
    @Transactional
    public OrderDetailView cancel(UUID userId, String orderNumber, String clientIp) {
        rateLimiter.consume("order-cancel", userId.toString());
        Order order = orders.lockByUserIdAndOrderNumber(userId, orderNumber).orElseThrow(OrderService::notFound);
        if (!order.isCancellableByCustomer()) {
            throw new ConflictException(
                    "ORDER_NOT_CANCELLABLE", "This order can no longer be cancelled online. Please contact us.");
        }
        cancellation.cancel(order, "Cancelled by the customer", userId, false);
        audit.record(userId, "ORDER_CANCELLED", "ORDER", order.getId(), Map.of("orderNumber", order.getOrderNumber()),
                clientIp);
        return views.detail(order);
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Order not found.");
    }
}
