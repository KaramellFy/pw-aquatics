package com.pwaquatics.shop.notification.infrastructure;

import com.pwaquatics.shop.notification.domain.OutboxMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

    /** Due messages, locked so that a concurrent dispatcher skips them. */
    @Query(value = """
            SELECT * FROM outbox_messages
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxMessage> lockDue(@Param("now") Instant now, @Param("limit") int limit);
}
