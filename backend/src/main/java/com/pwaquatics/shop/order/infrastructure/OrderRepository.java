package com.pwaquatics.shop.order.infrastructure;

import com.pwaquatics.shop.order.domain.Order;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query(value = "SELECT nextval('order_number_seq')", nativeQuery = true)
    long nextOrderSequence();

    Optional<Order> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    /** A customer's own order; someone else's order number finds nothing. */
    Optional<Order> findByUserIdAndOrderNumber(UUID userId, String orderNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.userId = :userId AND o.orderNumber = :orderNumber")
    Optional<Order> lockByUserIdAndOrderNumber(@Param("userId") UUID userId, @Param("orderNumber") String orderNumber);

    Optional<Order> findByOrderNumber(String orderNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.orderNumber = :orderNumber")
    Optional<Order> lockByOrderNumber(@Param("orderNumber") String orderNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> lockById(@Param("id") UUID id);

    @Query("SELECT o FROM Order o WHERE o.userId = :userId ORDER BY o.placedAt DESC, o.id DESC")
    Page<Order> findAllOf(@Param("userId") UUID userId, Pageable pageable);

    /** Unpaid orders whose payment deadline has passed, oldest first. */
    @Query("""
            SELECT o.id FROM Order o
            WHERE o.status = com.pwaquatics.shop.order.domain.OrderStatus.PENDING_PAYMENT AND o.paymentDueAt < :now
            ORDER BY o.paymentDueAt
            """)
    List<UUID> findOverdueIds(@Param("now") Instant now, Pageable pageable);
}
