package com.pwaquatics.shop.notification.domain;

import java.util.Objects;

/** A plain-text email to be delivered through the outbox. */
public record EmailMessage(String to, String replyTo, String subject, String body) {

    /**
     * The same message without its text. Kept after sending, so that it is still visible that
     * an email went out, while names, addresses and one-time links in the text are gone.
     */
    public EmailMessage withoutBody() {
        return new EmailMessage(to, replyTo, subject, "");
    }

    public EmailMessage {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(body, "body");
    }
}
