package com.pwaquatics.shop.audit.infrastructure;

import com.pwaquatics.shop.audit.domain.AuditLogEntry;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

    List<AuditLogEntry> findByActionOrderByIdAsc(String action);

    /** Erases the IP addresses of entries created before {@code cutoff}; the entries stay. */
    @Modifying
    @Query(value = "UPDATE audit_log SET ip_address = NULL WHERE ip_address IS NOT NULL AND created_at < :cutoff",
            nativeQuery = true)
    int eraseIpAddressesBefore(@Param("cutoff") Instant cutoff);
}
