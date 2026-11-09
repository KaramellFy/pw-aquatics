package com.pwaquatics.shop.notification.domain;

import java.util.Objects;

/** A plain-text email to be delivered through the outbox. */
public record EmailMessage(String to, String replyTo, String subject, String body) {

    public EmailMessage {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(body, "body");
    }
}
