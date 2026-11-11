package ru.anyforms.email.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.email.dto.EmailMessageDTO;
import ru.anyforms.email.dto.EmailMessageDetailsDTO;
import ru.anyforms.email.dto.QueueStatsDTO;
import ru.anyforms.email.model.EmailMessage;
import ru.anyforms.email.model.EmailMessageStatus;
import ru.anyforms.email.model.EmailType;
import ru.anyforms.email.repository.GetterEmailMessage;
import ru.anyforms.email.repository.SaverEmailMessage;
import ru.anyforms.email.service.EmailMessageAdminService;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
class EmailMessageAdminServiceImpl implements EmailMessageAdminService {

    static final int DEFAULT_LIMIT = 100;
    static final int MAX_LIMIT = 500;

    private final GetterEmailMessage getterEmailMessage;
    private final SaverEmailMessage saverEmailMessage;
    private final Clock clock;

    EmailMessageAdminServiceImpl(GetterEmailMessage getterEmailMessage,
                                 SaverEmailMessage saverEmailMessage,
                                 Clock clock) {
        this.getterEmailMessage = getterEmailMessage;
        this.saverEmailMessage = saverEmailMessage;
        this.clock = clock;
    }

    @Override
    public List<EmailMessageDTO> list(EmailMessageStatus status, EmailType type, String client, Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
        String clientFilter = client == null || client.isBlank() ? null : client.trim();
        return getterEmailMessage.getRecent(status, type, clientFilter, size).stream()
                .map(EmailMessageDTO::from)
                .toList();
    }

    @Override
    public EmailMessageDetailsDTO get(UUID id) {
        return EmailMessageDetailsDTO.from(find(id));
    }

    @Override
    public QueueStatsDTO stats() {
        return new QueueStatsDTO(getterEmailMessage.countByStatus());
    }

    @Override
    @Transactional
    public EmailMessageDTO retry(UUID id) {
        EmailMessage message = find(id);
        if (message.getStatus() != EmailMessageStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Повторить можно только неотправленное письмо, сейчас статус " + message.getStatus());
        }
        message.setStatus(EmailMessageStatus.QUEUED);
        message.setAttempts(0);
        message.setNextAttemptAt(clock.instant());
        message.setLockedUntil(null);
        message.setLastError(null);
        EmailMessage saved = saverEmailMessage.save(message);
        log.info("Письмо {} возвращено в очередь вручную", id);
        return EmailMessageDTO.from(saved);
    }

    private EmailMessage find(UUID id) {
        return getterEmailMessage.getById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Письмо не найдено: " + id));
    }
}
