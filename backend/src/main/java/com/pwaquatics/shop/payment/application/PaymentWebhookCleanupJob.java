package com.pwaquatics.shop.payment.application;

import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.payment.infrastructure.WebhookEventLog;
import java.time.Clock;
import java.time.Duration;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Deletes payment webhook deliveries after {@code app.retention.payment-webhook-events}. */
@Component
public class PaymentWebhookCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookCleanupJob.class);

    private final WebhookEventLog events;
    private final Duration retention;
    private final Clock clock;

    PaymentWebhookCleanupJob(WebhookEventLog events, AppProperties properties, Clock clock) {
        this.events = events;
        this.retention = properties.retention().paymentWebhookEvents();
        this.clock = clock;
    }

    @Scheduled(cron = "0 47 3 * * *", zone = "Europe/Berlin")
    @SchedulerLock(name = "payment-webhook-cleanup", lockAtMostFor = "PT10M")
    @Transactional
    void run() {
        int deleted = purge();
        if (deleted > 0) {
            log.info("Removed {} old payment webhook deliveries", deleted);
        }
    }

    /** @return the number of deliveries deleted */
    @Transactional
    public int purge() {
        return events.deleteReceivedBefore(clock.instant().minus(retention));
    }
}
