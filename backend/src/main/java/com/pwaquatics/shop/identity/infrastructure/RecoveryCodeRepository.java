package com.pwaquatics.shop.identity.infrastructure;

import com.pwaquatics.shop.identity.domain.RecoveryCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {

    @Query("SELECT c FROM RecoveryCode c WHERE c.userId = :userId AND c.codeHash = :codeHash AND c.usedAt IS NULL")
    Optional<RecoveryCode> findUnused(UUID userId, String codeHash);

    @Query("SELECT COUNT(c) FROM RecoveryCode c WHERE c.userId = :userId AND c.usedAt IS NULL")
    int countUnused(UUID userId);

    @Modifying
    @Query("DELETE FROM RecoveryCode c WHERE c.userId = :userId")
    void deleteByUserId(UUID userId);
}
