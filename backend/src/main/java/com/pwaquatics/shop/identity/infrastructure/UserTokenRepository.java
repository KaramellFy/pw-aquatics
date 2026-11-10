package com.pwaquatics.shop.identity.infrastructure;

import com.pwaquatics.shop.identity.domain.UserToken;
import com.pwaquatics.shop.identity.domain.UserTokenType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTokenRepository extends JpaRepository<UserToken, UUID> {

    @Query("SELECT t FROM UserToken t JOIN FETCH t.user WHERE t.tokenHash = :hash")
    Optional<UserToken> findByTokenHash(@Param("hash") String hash);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM UserToken t WHERE t.user.id = :userId AND t.type = :type")
    void deleteByUserAndType(@Param("userId") UUID userId, @Param("type") UserTokenType type);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM UserToken t WHERE t.user.id = :userId")
    void deleteByUser(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM UserToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
