package com.pwaquatics.shop.audit.infrastructure;

import com.pwaquatics.shop.audit.domain.AuditLogEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

    List<AuditLogEntry> findByActionOrderByIdAsc(String action);
}
