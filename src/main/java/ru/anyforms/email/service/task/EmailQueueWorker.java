package ru.anyforms.email.service.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.anyforms.email.exception.EmailDeliveryException;
import ru.anyforms.email.model.EmailMessage;
import ru.anyforms.email.model.EmailMessageStatus;
import ru.anyforms.email.model.EmailSender;
import ru.anyforms.email.repository.EmailMessageQueue;
import ru.anyforms.email.repository.GetterEmailSender;
import ru.anyforms.email.repository.SaverEmailMessage;
import ru.anyforms.email.service.provider.EmailProviderRegistry;
import ru.anyforms.email.service.provider.OutgoingEmail;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
class EmailQueueWorker {

    static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            Duration.ofHours(1),
            Duration.ofHours(3));

    private final EmailMessageQueue emailMessageQueue;
    private final SaverEmailMessage saverEmailMessage;
    private final GetterEmailSender getterEmailSender;
    private final EmailProviderRegistry emailProviderRegistry;
    private final Clock clock;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration lock;

    EmailQueueWorker(EmailMessageQueue emailMessageQueue,
                     SaverEmailMessage saverEmailMessage,
                     GetterEmailSender getterEmailSender,
                     EmailProviderRegistry emailProviderRegistry,
                     Clock clock,
                     @Value("${email.queue.batch-size}") int batchSize,
                     @Value("${email.queue.max-attempts}") int maxAttempts,
                     @Value("${email.queue.lock-seconds}") long lockSeconds) {
        this.emailMessageQueue = emailMessageQueue;
        this.saverEmailMessage = saverEmailMessage;
        this.getterEmailSender = getterEmailSender;
        this.emailProviderRegistry = emailProviderRegistry;
        this.clock = clock;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.lock = Duration.ofSeconds(lockSeconds);
    }

    @Scheduled(fixedDelayString = "${email.queue.fixed-delay-ms}", initialDelayString = "${email.queue.initial-delay-ms}")
    public void run() {
        List<EmailMessage> batch;
        try {
            batch = emailMessageQueue.claimDue(batchSize, lock);
        } catch (Exception e) {
            log.error("Не удалось взять письма из очереди", e);
            return;
        }
        for (EmailMessage message : batch) {
            try {
                deliver(message);
            } catch (Exception e) {
                log.error("Письмо {}: не удалось сохранить результат отправки", message.getId(), e);
            }
        }
    }

    void deliver(EmailMessage message) {
        if (message.getAttempts() > maxAttempts) {
            finish(message, "Превышено число попыток отправки: " + maxAttempts, true);
            return;
        }
        EmailSender sender = getterEmailSender.getByType(message.getType())
                .filter(EmailSender::isConfigured)
                .orElse(null);
        if (sender == null) {
            finish(message, "Не настроен адрес отправителя для писем " + message.getType(), false);
            return;
        }
        try {
            String providerMessageId = emailProviderRegistry.get(message.getType()).send(toOutgoing(message, sender));
            message.setStatus(EmailMessageStatus.SENT);
            message.setSentAt(clock.instant());
            message.setProviderMessageId(providerMessageId);
            message.setLockedUntil(null);
            message.setLastError(null);
            saverEmailMessage.save(message);
            log.info("Письмо {} отправлено: тип {}, попытка {}, id у провайдера {}",
                    message.getId(), message.getType(), message.getAttempts(), providerMessageId);
        } catch (EmailDeliveryException e) {
            finish(message, e.getMessage(), e.isPermanent());
        } catch (RuntimeException e) {
            finish(message, e.getMessage(), false);
        }
    }

    private void finish(EmailMessage message, String error, boolean permanent) {
        message.setLastError(crop(error));
        message.setLockedUntil(null);
        if (permanent || message.getAttempts() >= maxAttempts) {
            message.setStatus(EmailMessageStatus.FAILED);
            log.error("Письмо {} не отправлено, попыток {}: {}", message.getId(), message.getAttempts(), error);
        } else {
            Instant next = clock.instant().plus(retryDelay(message.getAttempts()));
            message.setStatus(EmailMessageStatus.QUEUED);
            message.setNextAttemptAt(next);
            log.warn("Письмо {} будет повторено в {}: попытка {} из {} не удалась: {}",
                    message.getId(), next, message.getAttempts(), maxAttempts, error);
        }
        saverEmailMessage.save(message);
    }

    static Duration retryDelay(int attempt) {
        int index = Math.max(0, Math.min(attempt - 1, RETRY_DELAYS.size() - 1));
        return RETRY_DELAYS.get(index);
    }

    private static OutgoingEmail toOutgoing(EmailMessage message, EmailSender sender) {
        String fromName = message.getFromName() != null ? message.getFromName() : sender.getFromName();
        String replyTo = message.getReplyTo() != null ? message.getReplyTo() : sender.getReplyTo();
        return new OutgoingEmail(message.getId(), message.getToAddress(), sender.getFromAddress(), fromName, replyTo,
                message.getSubject(), message.getHtml());
    }

    private static String crop(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
