package com.pwaquatics.shop.content.infrastructure;

import com.pwaquatics.shop.content.domain.ContactMessage;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, UUID> {

    @Modifying
    @Query(value = "DELETE FROM contact_messages WHERE created_at < :cutoff", nativeQuery = true)
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
