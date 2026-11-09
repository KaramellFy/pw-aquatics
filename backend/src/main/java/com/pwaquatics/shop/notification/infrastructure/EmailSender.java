package com.pwaquatics.shop.notification.infrastructure;

import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.notification.domain.EmailMessage;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Sends plain-text emails over SMTP (Mailpit in development). */
@Component
public class EmailSender {

    private final JavaMailSender mailSender;
    private final AppProperties properties;

    public EmailSender(JavaMailSender mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public void send(EmailMessage email) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mail().from());
        message.setTo(email.to());
        if (email.replyTo() != null) {
            message.setReplyTo(email.replyTo());
        }
        message.setSubject(email.subject());
        message.setText(email.body());
        mailSender.send(message);
    }
}
