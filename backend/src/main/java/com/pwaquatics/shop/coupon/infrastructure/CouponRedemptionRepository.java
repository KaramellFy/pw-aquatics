package com.pwaquatics.shop.coupon.infrastructure;

import com.pwaquatics.shop.coupon.domain.CouponRedemption;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, UUID> {

    /** Redemptions of a customer whose orders were not cancelled. */
    long countByCouponIdAndUserIdAndReleasedAtIsNull(UUID couponId, UUID userId);

    Optional<CouponRedemption> findByOrderId(UUID orderId);

    boolean existsByCouponId(UUID couponId);
}
