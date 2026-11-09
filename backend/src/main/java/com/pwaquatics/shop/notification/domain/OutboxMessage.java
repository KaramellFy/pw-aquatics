package com.pwaquatics.shop.notification.domain;

import com.pwaquatics.shop.common.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A side effect (currently: an email) recorded in the same transaction as the business change
 * and delivered later by {@link com.pwaquatics.shop.notification.application.OutboxDispatcher}.
 */
@Entity
@Table(name = "outbox_messages")
public class OutboxMessage extends AbstractEntity {

    public static final String TYPE_EMAIL = "EMAIL";

    @Column(nullable = false, length = 50)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private EmailMessage payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected OutboxMessage() {}

    public static OutboxMessage email(EmailMessage email, Instant now) {
        OutboxMessage message = new OutboxMessage();
        message.type = TYPE_EMAIL;
        message.payload = email;
        message.createdAt = now;
        message.nextAttemptAt = now;
        return message;
    }

    public void markSent(Instant now) {
        status = OutboxStatus.SENT;
        sentAt = now;
        lastError = null;
    }

    /** Records a failed attempt and schedules a retry with exponential backoff (1, 2, 4 … minutes). */
    public void markFailed(String error, Instant now, int maxAttempts) {
        attempts++;
        lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
        if (attempts >= maxAttempts) {
            status = OutboxStatus.FAILED;
        } else {
            nextAttemptAt = now.plus(Duration.ofMinutes(1L << Math.min(attempts - 1, 10)));
        }
    }

    public String getType() {
        return type;
    }

    public EmailMessage getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }
}
