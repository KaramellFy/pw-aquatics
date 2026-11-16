package com.pwaquatics.shop.coupon.infrastructure;

import com.pwaquatics.shop.coupon.domain.Coupon;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    /** @param code a normalised code (upper case) */
    Optional<Coupon> findByCode(String code);

    boolean existsByCode(String code);

    /** All coupons, newest first (a shop has a few dozen; the admin list filters them). */
    List<Coupon> findAllByOrderByCreatedAtDescIdDesc();

    /** Locks the coupon for the rest of the order transaction (always after the products). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    Optional<Coupon> lockById(@Param("id") UUID id);

    /**
     * Counts one redemption, but only while the coupon is switched on and below its total
     * limit, so a limited coupon can never be redeemed more often than allowed.
     *
     * @return 1 if counted, 0 if the coupon cannot be redeemed (any more)
     */
    @Modifying
    @Query(value = """
            UPDATE coupons SET times_redeemed = times_redeemed + 1
            WHERE id = :id AND active
              AND (usage_limit_total IS NULL OR times_redeemed < usage_limit_total)
            """, nativeQuery = true)
    int countRedemption(@Param("id") UUID id);

    /** Gives a redemption back (its order was cancelled). */
    @Modifying
    @Query(value = "UPDATE coupons SET times_redeemed = times_redeemed - 1 WHERE id = :id AND times_redeemed > 0",
            nativeQuery = true)
    int releaseRedemption(@Param("id") UUID id);
}
