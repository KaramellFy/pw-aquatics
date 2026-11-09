package com.pwaquatics.shop.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.pwaquatics.shop.notification.application.OutboxDispatcher;
import com.pwaquatics.shop.notification.application.OutboxService;
import com.pwaquatics.shop.notification.domain.EmailMessage;
import com.pwaquatics.shop.notification.domain.OutboxStatus;
import com.pwaquatics.shop.notification.infrastructure.OutboxMessageRepository;
import com.pwaquatics.shop.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class OutboxDispatcherTest {

    @MockitoBean JavaMailSender mailSender;
    @Autowired OutboxService outboxService;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired OutboxMessageRepository repository;
    @Autowired TransactionTemplate transaction;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void sendsDueEmailsAndMarksThemSent() {
        enqueue();

        assertThat(dispatcher.dispatchDue()).isEqualTo(1);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly("customer@example.com");
        assertThat(sent.getValue().getSubject()).isEqualTo("Hello");
        assertThat(repository.findAll()).singleElement()
                .satisfies(message -> assertThat(message.getStatus()).isEqualTo(OutboxStatus.SENT));
        assertThat(dispatcher.dispatchDue()).isZero();
    }

    @Test
    void schedulesARetryWhenSendingFails() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));
        enqueue();

        dispatcher.dispatchDue();

        assertThat(repository.findAll()).singleElement().satisfies(message -> {
            assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(message.getAttempts()).isEqualTo(1);
            assertThat(message.getLastError()).contains("SMTP down");
        });
        // Not due again yet (backoff), so nothing is retried immediately.
        assertThat(dispatcher.dispatchDue()).isZero();
    }

    @Test
    void enqueueRequiresATransaction() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> outboxService.enqueueEmail(email()))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    private void enqueue() {
        transaction.executeWithoutResult(status -> outboxService.enqueueEmail(email()));
    }

    private static EmailMessage email() {
        return new EmailMessage("customer@example.com", null, "Hello", "Body");
    }
}
