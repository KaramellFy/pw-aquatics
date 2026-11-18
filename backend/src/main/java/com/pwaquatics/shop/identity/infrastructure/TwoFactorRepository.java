package com.pwaquatics.shop.identity.infrastructure;

import com.pwaquatics.shop.identity.domain.TwoFactor;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TwoFactorRepository extends JpaRepository<TwoFactor, UUID> {

    Optional<TwoFactor> findByUserId(UUID userId);

    /** Locked, so two requests with the same code cannot both be accepted. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TwoFactor t WHERE t.userId = :userId")
    Optional<TwoFactor> lockByUserId(UUID userId);

    @Query("SELECT COUNT(t) > 0 FROM TwoFactor t WHERE t.userId = :userId AND t.secretCiphertext IS NOT NULL")
    boolean isEnabledFor(UUID userId);

    @Modifying
    @Query("DELETE FROM TwoFactor t WHERE t.userId = :userId")
    void deleteByUserId(UUID userId);
}
