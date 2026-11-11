package com.pwaquatics.shop.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwaquatics.shop.audit.application.AuditRetentionJob;
import com.pwaquatics.shop.content.application.ContactMessageCleanupJob;
import com.pwaquatics.shop.notification.application.OutboxCleanupJob;
import com.pwaquatics.shop.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Personal data that no law requires us to keep is removed on schedule
 * (docs/DATABASE.md §6; defaults: emails 30 days, audit IPs 90 days, contact messages 365 days).
 */
@IntegrationTest
class DataRetentionTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired OutboxCleanupJob outboxCleanup;
    @Autowired AuditRetentionJob auditRetention;
    @Autowired ContactMessageCleanupJob contactCleanup;

    @Test
    void oldEmailsAreDeletedButPendingOnesNever() {
        UUID oldSent = outbox("SENT", "40 days", "31 days");
        UUID recentSent = outbox("SENT", "2 days", "1 day");
        UUID oldFailed = outbox("FAILED", "31 days", null);
        UUID recentFailed = outbox("FAILED", "2 days", null);
        UUID stillPending = outbox("PENDING", "60 days", null);

        assertThat(outboxCleanup.purge()).isGreaterThanOrEqualTo(2);

        assertThat(exists("outbox_messages", oldSent)).isFalse();
        assertThat(exists("outbox_messages", oldFailed)).isFalse();
        assertThat(exists("outbox_messages", recentSent)).isTrue();
        assertThat(exists("outbox_messages", recentFailed)).isTrue();
        assertThat(exists("outbox_messages", stillPending)).isTrue();
        jdbc.update("DELETE FROM outbox_messages WHERE id IN (?, ?, ?)", recentSent, recentFailed, stillPending);
    }

    @Test
    void auditEntriesLoseTheirIpAddressButStay() {
        UUID entity = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO audit_log (action, entity_type, entity_id, ip_address, created_at) VALUES
                    ('RETENTION_TEST_OLD', 'TEST', ?, '203.0.113.7', now() - interval '91 days'),
                    ('RETENTION_TEST_NEW', 'TEST', ?, '203.0.113.8', now() - interval '1 day')
                """, entity, entity);

        assertThat(auditRetention.purge()).isGreaterThanOrEqualTo(1);

        assertThat(jdbc.queryForList(
                        "SELECT action || ':' || coalesce(host(ip_address), '-') FROM audit_log WHERE entity_id = ? ORDER BY id",
                        String.class, entity))
                .containsExactly("RETENTION_TEST_OLD:-", "RETENTION_TEST_NEW:203.0.113.8");
    }

    @Test
    void contactMessagesAreDeletedAfterAYear() {
        UUID old = contact("366 days");
        UUID recent = contact("300 days");

        assertThat(contactCleanup.purge()).isGreaterThanOrEqualTo(1);

        assertThat(exists("contact_messages", old)).isFalse();
        assertThat(exists("contact_messages", recent)).isTrue();
        jdbc.update("DELETE FROM contact_messages WHERE id = ?", recent);
    }

    private UUID outbox(String status, String createdAgo, String sentAgo) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO outbox_messages (id, type, payload, status, created_at, sent_at)
                VALUES (?, 'EMAIL', '{"to":"a@example.com","subject":"s","body":""}'::jsonb, ?,
                        now() - ?::interval, now() - ?::interval)
                """, id, status, createdAgo, sentAgo);
        return id;
    }

    private UUID contact(String createdAgo) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO contact_messages (id, name, email, subject, message, created_at)
                VALUES (?, 'Anna', 'anna@example.com', 'Question', 'A question about shrimp.', now() - ?::interval)
                """, id, createdAgo);
        return id;
    }

    private boolean exists(String table, UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE id = ?", Integer.class, id) == 1;
    }
}
