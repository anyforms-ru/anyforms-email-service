package ru.anyforms.email.dto;

import ru.anyforms.email.model.EmailMessage;
import ru.anyforms.email.model.EmailMessageStatus;
import ru.anyforms.email.model.EmailType;

import java.time.Instant;
import java.util.UUID;

public record EmailMessageDTO(
        UUID id,
        String client,
        String idempotencyKey,
        EmailType type,
        String templateCode,
        String to,
        String fromName,
        String replyTo,
        String subject,
        EmailMessageStatus status,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        String providerMessageId,
        Instant sentAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static EmailMessageDTO from(EmailMessage m) {
        return new EmailMessageDTO(m.getId(), m.getClient(), m.getIdempotencyKey(), m.getType(), m.getTemplateCode(),
                m.getToAddress(), m.getFromName(), m.getReplyTo(), m.getSubject(), m.getStatus(), m.getAttempts(),
                m.getNextAttemptAt(), m.getLastError(), m.getProviderMessageId(), m.getSentAt(), m.getCreatedAt(),
                m.getUpdatedAt());
    }
}
