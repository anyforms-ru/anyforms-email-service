package ru.anyforms.email.repository;

import ru.anyforms.email.model.EmailMessage;
import ru.anyforms.email.model.EmailMessageStatus;
import ru.anyforms.email.model.EmailType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface GetterEmailMessage {
    Optional<EmailMessage> getById(UUID id);

    Optional<EmailMessage> getByClientAndIdempotencyKey(String client, String idempotencyKey);

    List<EmailMessage> getRecent(EmailMessageStatus status, EmailType type, String client, int limit);

    Map<EmailMessageStatus, Long> countByStatus();
}
