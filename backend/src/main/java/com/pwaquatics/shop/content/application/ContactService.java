package com.pwaquatics.shop.content.application;

import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.content.domain.ContactMessage;
import com.pwaquatics.shop.content.infrastructure.ContactMessageRepository;
import com.pwaquatics.shop.notification.application.OutboxService;
import com.pwaquatics.shop.notification.domain.EmailMessage;
import com.pwaquatics.shop.security.ratelimit.RateLimiter;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores contact messages and notifies the shop inbox. */
@Service
public class ContactService {

    static final String RATE_LIMIT = "contact";
    private static final Logger log = LoggerFactory.getLogger(ContactService.class);

    private final ContactMessageRepository repository;
    private final OutboxService outbox;
    private final RateLimiter rateLimiter;
    private final AppProperties properties;
    private final Clock clock;

    public ContactService(
            ContactMessageRepository repository,
            OutboxService outbox,
            RateLimiter rateLimiter,
            AppProperties properties,
            Clock clock) {
        this.repository = repository;
        this.outbox = outbox;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Accepts a contact message from {@code clientIp}.
     *
     * <p>Rate limited per IP. Submissions that filled the hidden honeypot field are
     * silently dropped (the caller still sees success, so bots learn nothing).
     */
    @Transactional
    public void submit(SubmitContactMessage command, String clientIp) {
        rateLimiter.consume(RATE_LIMIT, clientIp);
        if (command.honeypot() != null && !command.honeypot().isBlank()) {
            log.info("Contact form honeypot triggered; message dropped");
            return;
        }

        ContactMessage message = repository.save(new ContactMessage(
                command.name(), command.email(), command.subject(), command.message(), command.orderNumber(),
                clock.instant()));

        outbox.enqueueEmail(new EmailMessage(
                properties.mail().shopInbox(),
                message.getEmail(),
                "[Contact] " + message.getSubject(),
                """
                New message from the contact form.

                Name: %s
                Email: %s
                Order number: %s

                %s
                """.formatted(
                        message.getName(),
                        message.getEmail(),
                        message.getOrderNumber() == null ? "–" : message.getOrderNumber(),
                        message.getMessage())));
    }
}
