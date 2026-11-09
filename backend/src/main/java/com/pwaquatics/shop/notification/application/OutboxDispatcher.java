package com.pwaquatics.shop.notification.application;

import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.notification.domain.OutboxMessage;
import com.pwaquatics.shop.notification.infrastructure.EmailSender;
import com.pwaquatics.shop.notification.infrastructure.OutboxMessageRepository;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Delivers due outbox messages with retries and exponential backoff. */
@Service
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxMessageRepository repository;
    private final EmailSender emailSender;
    private final AppProperties properties;
    private final Clock clock;

    public OutboxDispatcher(
            OutboxMessageRepository repository, EmailSender emailSender, AppProperties properties, Clock clock) {
        this.repository = repository;
        this.emailSender = emailSender;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Sends one batch of due messages. Rows stay locked until the transaction ends, so a
     * second dispatcher (if ever started) skips them.
     *
     * @return the number of messages processed
     */
    @Transactional
    public int dispatchDue() {
        List<OutboxMessage> due = repository.lockDue(clock.instant(), properties.outbox().batchSize());
        for (OutboxMessage message : due) {
            try {
                if (OutboxMessage.TYPE_EMAIL.equals(message.getType())) {
                    emailSender.send(message.getPayload());
                }
                message.markSent(clock.instant());
            } catch (RuntimeException ex) {
                message.markFailed(ex.getClass().getSimpleName() + ": " + ex.getMessage(), clock.instant(),
                        properties.outbox().maxAttempts());
                log.warn("Outbox message {} failed (attempt {}): {}", message.getId(), message.getAttempts(),
                        ex.getClass().getSimpleName());
            }
        }
        return due.size();
    }
}
