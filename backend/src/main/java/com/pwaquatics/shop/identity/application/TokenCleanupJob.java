package com.pwaquatics.shop.identity.application;

import com.pwaquatics.shop.identity.infrastructure.UserTokenRepository;
import java.time.Clock;
import java.time.Duration;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Removes one-time tokens a week after they expired. */
@Component
class TokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupJob.class);

    private final UserTokenRepository tokens;
    private final Clock clock;

    TokenCleanupJob(UserTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    @Scheduled(cron = "0 17 3 * * *", zone = "Europe/Berlin")
    @SchedulerLock(name = "user-token-cleanup", lockAtMostFor = "PT10M")
    @Transactional
    void run() {
        int deleted = tokens.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(7)));
        if (deleted > 0) {
            log.info("Removed {} expired account tokens", deleted);
        }
    }
}
