package com.pwaquatics.shop.content.application;

import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.content.infrastructure.ContactMessageRepository;
import java.time.Clock;
import java.time.Duration;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Deletes contact form messages after {@code app.retention.contact-messages}. */
@Component
public class ContactMessageCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ContactMessageCleanupJob.class);

    private final ContactMessageRepository repository;
    private final Duration retention;
    private final Clock clock;

    ContactMessageCleanupJob(ContactMessageRepository repository, AppProperties properties, Clock clock) {
        this.repository = repository;
        this.retention = properties.retention().contactMessages();
        this.clock = clock;
    }

    @Scheduled(cron = "0 57 3 * * *", zone = "Europe/Berlin")
    @SchedulerLock(name = "contact-message-cleanup", lockAtMostFor = "PT10M")
    void run() {
        int deleted = purge();
        if (deleted > 0) {
            log.info("Removed {} old contact messages", deleted);
        }
    }

    /** @return the number of messages deleted */
    @Transactional
    public int purge() {
        return repository.deleteCreatedBefore(clock.instant().minus(retention));
    }
}
