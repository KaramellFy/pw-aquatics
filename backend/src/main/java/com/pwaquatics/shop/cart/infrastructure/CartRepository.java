package com.pwaquatics.shop.cart.infrastructure;

import com.pwaquatics.shop.cart.domain.Cart;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Carts are always loaded with a row lock, so two simultaneous requests for the same
 * cart (double click, two tabs) are processed one after the other.
 */
public interface CartRepository extends JpaRepository<Cart, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cart c WHERE c.userId = :userId")
    Optional<Cart> lockByUserId(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cart c WHERE c.anonymousTokenHash = :hash")
    Optional<Cart> lockByTokenHash(@Param("hash") String hash);

    /** Creates the customer's cart if it does not exist yet; safe under concurrent requests. */
    @Modifying
    @Query(value = """
            INSERT INTO carts (id, user_id, created_at, updated_at)
            VALUES (:id, :userId, :now, :now)
            ON CONFLICT (user_id) DO NOTHING
            """, nativeQuery = true)
    void insertForUserIfAbsent(@Param("id") UUID id, @Param("userId") UUID userId, @Param("now") Instant now);

    // The bulk deletes rely on the database's ON DELETE CASCADE to remove the cart items.

    @Modifying
    @Query("DELETE FROM Cart c WHERE c.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM Cart c WHERE c.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
