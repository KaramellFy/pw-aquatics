package com.pwaquatics.shop.audit.application;

import com.pwaquatics.shop.audit.domain.AuditLogEntry;
import com.pwaquatics.shop.audit.infrastructure.AuditLogRepository;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records security-relevant and administrative actions. */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Writes an audit record in the caller's transaction (or its own if there is none).
     *
     * @param details small, non-sensitive context; never passwords, tokens or card data
     */
    @Transactional
    public void record(UUID actorUserId, String action, String entityType, UUID entityId, Map<String, Object> details,
            String ip) {
        repository.save(new AuditLogEntry(actorUserId, action, entityType, entityId, details, parse(ip),
                clock.instant()));
    }

    /** Shortcut for actions a user performs on their own account. */
    @Transactional
    public void recordUserAction(UUID userId, String action, String ip) {
        record(userId, action, "USER", userId, null, ip);
    }

    private static InetAddress parse(String ip) {
        if (ip == null || ip.isBlank() || !ip.matches("[0-9a-fA-F:.]+")) {
            return null; // only literal addresses, so this never triggers a DNS lookup
        }
        try {
            return InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
