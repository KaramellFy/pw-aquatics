package com.pwaquatics.shop.payment.infrastructure;

import com.pwaquatics.shop.payment.domain.Payment;
import com.pwaquatics.shop.payment.domain.PaymentMethod;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderIdAndProvider(UUID orderId, PaymentMethod provider);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.orderId = :orderId AND p.provider = :provider")
    Optional<Payment> lockByOrderIdAndProvider(@Param("orderId") UUID orderId, @Param("provider") PaymentMethod provider);

    /** Only the order id (the payment itself is loaded with its lock, after the order's). */
    @Query("SELECT p.orderId FROM Payment p WHERE p.providerReference = :reference")
    Optional<UUID> findOrderIdByProviderReference(@Param("reference") String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.providerReference = :reference")
    Optional<Payment> lockByProviderReference(@Param("reference") String reference);
}
