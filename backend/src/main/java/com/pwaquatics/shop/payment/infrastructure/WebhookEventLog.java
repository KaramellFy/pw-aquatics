package com.pwaquatics.shop.payment.infrastructure;

import com.pwaquatics.shop.common.ids.Ids;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Received payment webhook deliveries ({@code payment_webhook_events}). The unique
 * {@code (provider, event_id)} makes a repeated delivery find the row of the first one.
 */
@Repository
public class WebhookEventLog {

    private final JdbcClient jdbc;

    WebhookEventLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Records a delivery in its own transaction (so it stays recorded even if processing
     * fails) and returns the row id — of this delivery, or of the earlier one with the same id.
     *
     * @param payload reduced JSON (ids, amounts and statuses only)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID store(String provider, String eventId, String eventType, String payload, Instant now) {
        jdbc.sql("""
                        INSERT INTO payment_webhook_events (id, provider, event_id, event_type, payload, received_at)
                        VALUES (:id, :provider, :eventId, :eventType, CAST(:payload AS jsonb), :now)
                        ON CONFLICT (provider, event_id) DO NOTHING
                        """)
                .param("id", Ids.newId())
                .param("provider", provider)
                .param("eventId", eventId)
                .param("eventType", eventType)
                .param("payload", payload)
                .param("now", java.sql.Timestamp.from(now))
                .update();
        return jdbc.sql("SELECT id FROM payment_webhook_events WHERE provider = :provider AND event_id = :eventId")
                .param("provider", provider)
                .param("eventId", eventId)
                .query(UUID.class)
                .single();
    }

    /**
     * Locks the row for the caller's transaction, so two deliveries of one event are processed
     * one after the other.
     *
     * @return when it was processed, or empty if it still needs processing
     */
    public Optional<Instant> lockAndGetProcessedAt(UUID id) {
        return jdbc.sql("SELECT processed_at FROM payment_webhook_events WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query((rs, row) -> Optional.ofNullable(rs.getTimestamp("processed_at")).map(java.sql.Timestamp::toInstant))
                .single();
    }

    /** @param note why the event led to no change, or null */
    public void markProcessed(UUID id, Instant now, String note) {
        jdbc.sql("UPDATE payment_webhook_events SET processed_at = :now, error = :note WHERE id = :id")
                .param("id", id)
                .param("now", java.sql.Timestamp.from(now))
                .param("note", note)
                .update();
    }

    /** Keeps the reason for a failed processing attempt; the provider will deliver the event again. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordError(UUID id, String error) {
        jdbc.sql("UPDATE payment_webhook_events SET error = :error WHERE id = :id AND processed_at IS NULL")
                .param("id", id)
                .param("error", error)
                .update();
    }

    /** @return the number of deliveries deleted */
    public int deleteReceivedBefore(Instant cutoff) {
        return jdbc.sql("DELETE FROM payment_webhook_events WHERE received_at < :cutoff")
                .param("cutoff", java.sql.Timestamp.from(cutoff))
                .update();
    }
}
