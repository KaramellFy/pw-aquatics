package com.pwaquatics.shop.notification.application;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the outbox dispatcher periodically. Disabled in tests via {@code app.outbox.scheduler-enabled}. */
@Component
@ConditionalOnProperty(name = "app.outbox.scheduler-enabled", havingValue = "true", matchIfMissing = true)
class OutboxScheduler {

    private final OutboxDispatcher dispatcher;

    OutboxScheduler(OutboxDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval}")
    @SchedulerLock(name = "outbox-dispatch", lockAtMostFor = "PT2M")
    void run() {
        dispatcher.dispatchDue();
    }
}
