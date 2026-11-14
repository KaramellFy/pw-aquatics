package com.pwaquatics.shop.content.infrastructure;

import com.pwaquatics.shop.content.domain.ContactMessage;
import com.pwaquatics.shop.content.domain.ContactMessageStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, UUID> {

    Page<ContactMessage> findByStatusInOrderByCreatedAtDesc(Collection<ContactMessageStatus> statuses, Pageable pageable);

    long countByStatus(ContactMessageStatus status);

    @Modifying
    @Query(value = "DELETE FROM contact_messages WHERE created_at < :cutoff", nativeQuery = true)
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
