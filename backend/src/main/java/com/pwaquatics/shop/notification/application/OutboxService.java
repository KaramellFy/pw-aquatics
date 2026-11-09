package com.pwaquatics.shop.notification.application;

import com.pwaquatics.shop.notification.domain.EmailMessage;
import com.pwaquatics.shop.notification.domain.OutboxMessage;
import com.pwaquatics.shop.notification.infrastructure.OutboxMessageRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Entry point for other modules to schedule notifications. */
@Service
public class OutboxService {

    private final OutboxMessageRepository repository;
    private final Clock clock;

    public OutboxService(OutboxMessageRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Records an email to be sent after the surrounding transaction commits.
     * Must be called inside the caller's business transaction, so the email is
     * sent if and only if the business change is saved.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueEmail(EmailMessage email) {
        repository.save(OutboxMessage.email(email, clock.instant()));
    }
}
