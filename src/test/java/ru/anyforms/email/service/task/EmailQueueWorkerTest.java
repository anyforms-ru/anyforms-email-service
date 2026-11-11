package ru.anyforms.email.service.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.anyforms.email.exception.EmailDeliveryException;
import ru.anyforms.email.model.EmailMessage;
import ru.anyforms.email.model.EmailMessageStatus;
import ru.anyforms.email.model.EmailSender;
import ru.anyforms.email.model.EmailType;
import ru.anyforms.email.repository.EmailMessageQueue;
import ru.anyforms.email.repository.GetterEmailSender;
import ru.anyforms.email.repository.SaverEmailMessage;
import ru.anyforms.email.service.provider.EmailProvider;
import ru.anyforms.email.service.provider.EmailProviderRegistry;
import ru.anyforms.email.service.provider.OutgoingEmail;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailQueueWorkerTest {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    private final EmailMessageQueue queue = mock(EmailMessageQueue.class);
    private final SaverEmailMessage saver = mock(SaverEmailMessage.class);
    private final GetterEmailSender senders = mock(GetterEmailSender.class);
    private final EmailProvider system = mock(EmailProvider.class);
    private final EmailProvider marketing = mock(EmailProvider.class);
    private EmailQueueWorker worker;

    @BeforeEach
    void setUp() {
        when(system.type()).thenReturn(EmailType.SYSTEM);
        when(marketing.type()).thenReturn(EmailType.MARKETING);
        worker = new EmailQueueWorker(queue, saver, senders, new EmailProviderRegistry(List.of(system, marketing)),
                Clock.fixed(NOW, ZoneOffset.UTC), 20, 3, 120);
        when(senders.getByType(EmailType.SYSTEM)).thenReturn(Optional.of(EmailSender.builder()
                .type(EmailType.SYSTEM).fromAddress("noreply@anyforms.ru").fromName("anyforms").replyTo("help@anyforms.ru").build()));
    }

    private static EmailMessage message(int attempts) {
        return EmailMessage.builder()
                .id(UUID.randomUUID())
                .type(EmailType.SYSTEM)
                .toAddress("client@example.com")
                .fromName("Команда di_gips")
                .subject("Тема")
                .html("<p/>")
                .status(EmailMessageStatus.SENDING)
                .attempts(attempts)
                .lockedUntil(NOW.plusSeconds(120))
                .build();
    }

    @Test
    void sentLetterUsesSenderAddressAndMessageOverrides() {
        when(system.send(any())).thenReturn("pb-1");
        EmailMessage m = message(1);

        worker.deliver(m);

        ArgumentCaptor<OutgoingEmail> captor = ArgumentCaptor.forClass(OutgoingEmail.class);
        verify(system).send(captor.capture());
        assertEquals("noreply@anyforms.ru", captor.getValue().fromAddress());
        assertEquals("Команда di_gips", captor.getValue().fromName());
        assertEquals("help@anyforms.ru", captor.getValue().replyTo());
        assertEquals(EmailMessageStatus.SENT, m.getStatus());
        assertEquals("pb-1", m.getProviderMessageId());
        assertEquals(NOW, m.getSentAt());
        assertNull(m.getLockedUntil());
        verify(saver).save(m);
    }

    @Test
    void transientErrorRequeuesWithBackoff() {
        when(system.send(any())).thenThrow(new EmailDeliveryException("SES недоступен", false));
        EmailMessage m = message(2);

        worker.deliver(m);

        assertEquals(EmailMessageStatus.QUEUED, m.getStatus());
        assertEquals(NOW.plus(Duration.ofMinutes(5)), m.getNextAttemptAt());
        assertEquals("SES недоступен", m.getLastError());
    }

    @Test
    void permanentErrorFailsAtOnce() {
        when(system.send(any())).thenThrow(new EmailDeliveryException("MessageRejected", true));
        EmailMessage m = message(1);

        worker.deliver(m);

        assertEquals(EmailMessageStatus.FAILED, m.getStatus());
    }

    @Test
    void lastAttemptFails() {
        when(system.send(any())).thenThrow(new RuntimeException("timeout"));
        EmailMessage m = message(3);

        worker.deliver(m);

        assertEquals(EmailMessageStatus.FAILED, m.getStatus());
    }

    @Test
    void staleLetterOverLimitFailsWithoutSending() {
        EmailMessage m = message(4);

        worker.deliver(m);

        assertEquals(EmailMessageStatus.FAILED, m.getStatus());
        verify(system, never()).send(any());
    }

    @Test
    void missingSenderAddressWaitsForSettings() {
        when(senders.getByType(EmailType.MARKETING)).thenReturn(Optional.of(EmailSender.builder().type(EmailType.MARKETING).build()));
        EmailMessage m = message(1);
        m.setType(EmailType.MARKETING);

        worker.deliver(m);

        assertEquals(EmailMessageStatus.QUEUED, m.getStatus());
        verify(marketing, never()).send(any());
    }

    @Test
    void oneBrokenLetterDoesNotStopBatch() {
        EmailMessage broken = message(1);
        EmailMessage ok = message(1);
        when(queue.claimDue(20, Duration.ofSeconds(120))).thenReturn(List.of(broken, ok));
        when(system.send(any())).thenReturn("id");
        when(saver.save(broken)).thenThrow(new RuntimeException("db"));

        worker.run();

        verify(saver).save(ok);
        assertEquals(EmailMessageStatus.SENT, ok.getStatus());
    }

    @Test
    void retryDelaysGrowAndCap() {
        assertEquals(Duration.ofMinutes(1), EmailQueueWorker.retryDelay(1));
        assertEquals(Duration.ofHours(3), EmailQueueWorker.retryDelay(5));
        assertEquals(Duration.ofHours(3), EmailQueueWorker.retryDelay(50));
    }
}
