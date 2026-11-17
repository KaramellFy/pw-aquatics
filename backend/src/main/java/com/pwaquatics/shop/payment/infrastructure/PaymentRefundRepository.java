package com.pwaquatics.shop.payment.infrastructure;

import com.pwaquatics.shop.payment.domain.PaymentRefund;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, UUID> {

    Optional<PaymentRefund> findByIdempotencyKey(String idempotencyKey);

    List<PaymentRefund> findAllByPaymentIdOrderByCreatedAtAsc(UUID paymentId);

    /** Only the order id of a refund the provider reports on (the refund is locked after its order). */
    @Query("SELECT p.orderId FROM PaymentRefund r JOIN Payment p ON p.id = r.paymentId "
            + "WHERE r.providerReference = :reference")
    Optional<UUID> findOrderIdByProviderReference(@Param("reference") String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM PaymentRefund r WHERE r.providerReference = :reference")
    Optional<PaymentRefund> lockByProviderReference(@Param("reference") String reference);
}
