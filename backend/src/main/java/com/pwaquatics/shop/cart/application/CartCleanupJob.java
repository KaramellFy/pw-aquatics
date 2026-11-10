package com.pwaquatics.shop.cart.application;

import com.pwaquatics.shop.cart.infrastructure.CartRepository;
import java.time.Clock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Removes guest carts that have not been touched for {@code Cart.GUEST_LIFETIME}. */
@Component
class CartCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(CartCleanupJob.class);

    private final CartRepository carts;
    private final Clock clock;

    CartCleanupJob(CartRepository carts, Clock clock) {
        this.carts = carts;
        this.clock = clock;
    }

    @Scheduled(cron = "0 47 3 * * *", zone = "Europe/Berlin")
    @SchedulerLock(name = "guest-cart-cleanup", lockAtMostFor = "PT10M")
    @Transactional
    void run() {
        int deleted = carts.deleteExpired(clock.instant());
        if (deleted > 0) {
            log.info("Removed {} expired guest carts", deleted);
        }
    }
}
