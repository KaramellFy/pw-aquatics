package com.pwaquatics.shop.order.application;

import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs {@link OrderExpiry} every 15 minutes. */
@Component
class OrderExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);
    private static final int BATCH_SIZE = 50;

    private final OrderExpiry expiry;

    OrderExpiryJob(OrderExpiry expiry) {
        this.expiry = expiry;
    }

    @Scheduled(cron = "0 7/15 * * * *", zone = "Europe/Berlin")
    @SchedulerLock(name = "order-expiry", lockAtMostFor = "PT10M")
    void run() {
        List<UUID> overdue = expiry.overdueOrderIds(BATCH_SIZE);
        int cancelled = 0;
        for (UUID orderId : overdue) {
            try {
                if (expiry.expire(orderId)) {
                    cancelled++;
                }
            } catch (RuntimeException e) {
                // The next run tries again; no customer data in the log.
                log.error("Could not expire order {}", orderId, e);
            }
        }
        if (cancelled > 0) {
            log.info("Cancelled {} unpaid orders after their payment deadline", cancelled);
        }
    }
}
