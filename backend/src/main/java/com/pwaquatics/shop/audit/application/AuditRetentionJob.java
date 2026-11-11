package com.pwaquatics.shop.audit.application;

import com.pwaquatics.shop.audit.infrastructure.AuditLogRepository;
import com.pwaquatics.shop.common.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erases IP addresses from the audit log after {@code app.retention.audit-ip-addresses}.
 * They are needed only to investigate recent incidents (e.g. a series of failed logins);
 * the entries themselves stay as the record of what happened.
 */
@Component
public class AuditRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);

    private final AuditLogRepository repository;
    private final Duration ipRetention;
    private final Clock clock;

    AuditRetentionJob(AuditLogRepository repository, AppProperties properties, Clock clock) {
        this.repository = repository;
        this.ipRetention = properties.retention().auditIpAddresses();
        this.clock = clock;
    }

    @Scheduled(cron = "0 37 3 * * *", zone = "Europe/Berlin")
    @SchedulerLock(name = "audit-ip-retention", lockAtMostFor = "PT10M")
    void run() {
        int erased = purge();
        if (erased > 0) {
            log.info("Erased the IP addresses of {} audit entries", erased);
        }
    }

    /** @return the number of entries whose IP address was erased */
    @Transactional
    public int purge() {
        return repository.eraseIpAddressesBefore(clock.instant().minus(ipRetention));
    }
}
