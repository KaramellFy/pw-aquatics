package com.pwaquatics.shop.notification.application;

import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.notification.infrastructure.OutboxMessageRepository;
import java.time.Clock;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes old outbox rows. Their text is already erased when an email is sent; the rows
 * (recipient, subject) are removed after {@code app.retention.sent-emails}, emails that could
 * not be delivered after {@code app.retention.failed-emails}.
 */
@Component
public class OutboxCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupJob.class);

    private final OutboxMessageRepository repository;
    private final AppProperties.Retention retention;
    private final Clock clock;

    OutboxCleanupJob(OutboxMessageRepository repository, AppProperties properties, Clock clock) {
        this.repository = repository;
        this.retention = properties.retention();
        this.clock = clock;
    }

    @Scheduled(cron = "0 27 3 * * *", zone = "Europe/Berlin")
    @SchedulerLock(name = "outbox-cleanup", lockAtMostFor = "PT10M")
    void run() {
        int deleted = purge();
        if (deleted > 0) {
            log.info("Removed {} old outbox messages", deleted);
        }
    }

    /** @return the number of rows deleted */
    @Transactional
    public int purge() {
        Instant now = clock.instant();
        return repository.deleteSentBefore(now.minus(retention.sentEmails()))
                + repository.deleteFailedBefore(now.minus(retention.failedEmails()));
    }
}
