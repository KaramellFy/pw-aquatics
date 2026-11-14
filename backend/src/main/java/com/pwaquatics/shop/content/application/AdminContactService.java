package com.pwaquatics.shop.content.application;

import com.pwaquatics.shop.audit.application.Actor;
import com.pwaquatics.shop.audit.application.AuditService;
import com.pwaquatics.shop.common.error.NotFoundException;
import com.pwaquatics.shop.content.application.view.ContactMessageView;
import com.pwaquatics.shop.content.domain.ContactMessage;
import com.pwaquatics.shop.content.domain.ContactMessageStatus;
import com.pwaquatics.shop.content.infrastructure.ContactMessageRepository;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The contact inbox (docs/API.md §3.7). Messages are deleted automatically after the
 * retention period ({@link ContactMessageCleanupJob}); here they are only read and sorted.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminContactService {

    private static final Set<ContactMessageStatus> INBOX =
            EnumSet.of(ContactMessageStatus.NEW, ContactMessageStatus.READ, ContactMessageStatus.ANSWERED);

    private final ContactMessageRepository messages;
    private final AuditService audit;

    AdminContactService(ContactMessageRepository messages, AuditService audit) {
        this.messages = messages;
        this.audit = audit;
    }

    /**
     * Messages with the given status, newest first.
     *
     * @param status null for the inbox: everything except spam
     */
    @Transactional(readOnly = true)
    public Page<ContactMessageView> list(ContactMessageStatus status, int page, int size) {
        Set<ContactMessageStatus> statuses = status == null ? INBOX : EnumSet.of(status);
        return messages.findByStatusInOrderByCreatedAtDesc(statuses, PageRequest.of(page, size)).map(this::view);
    }

    /** How many messages there are per status (e.g. for the "new" badge). */
    @Transactional(readOnly = true)
    public Map<ContactMessageStatus, Long> counts() {
        Map<ContactMessageStatus, Long> counts = new EnumMap<>(ContactMessageStatus.class);
        for (ContactMessageStatus status : ContactMessageStatus.values()) {
            counts.put(status, messages.countByStatus(status));
        }
        return counts;
    }

    @Transactional
    public ContactMessageView changeStatus(UUID id, ContactMessageStatus status, Actor actor) {
        ContactMessage message = messages.findById(id).orElseThrow(() -> new NotFoundException("Message not found."));
        if (message.getStatus() != status) {
            ContactMessageStatus before = message.getStatus();
            message.changeStatus(status);
            audit.record(actor, "CONTACT_MESSAGE_STATUS_CHANGED", "CONTACT_MESSAGE", id,
                    Map.of("from", before.name(), "to", status.name()));
        }
        return view(message);
    }

    private ContactMessageView view(ContactMessage message) {
        return new ContactMessageView(message.getId(), message.getName(), message.getEmail(), message.getSubject(),
                message.getMessage(), message.getOrderNumber(), message.getStatus(), message.getCreatedAt());
    }
}
