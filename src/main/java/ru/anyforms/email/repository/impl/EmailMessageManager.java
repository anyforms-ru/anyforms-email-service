package ru.anyforms.email.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.anyforms.email.model.EmailMessage;
import ru.anyforms.email.model.EmailMessageStatus;
import ru.anyforms.email.model.EmailType;
import ru.anyforms.email.repository.EmailMessageCleaner;
import ru.anyforms.email.repository.EmailMessageQueue;
import ru.anyforms.email.repository.GetterEmailMessage;
import ru.anyforms.email.repository.SaverEmailMessage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@AllArgsConstructor
@Log4j2
class EmailMessageManager implements GetterEmailMessage, SaverEmailMessage, EmailMessageQueue, EmailMessageCleaner {

    private final EmailMessageRepo emailMessageRepo;
    private final Clock clock;

    @Override
    public Optional<EmailMessage> getById(UUID id) {
        try {
            return emailMessageRepo.findById(id);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Optional<EmailMessage> getByClientAndIdempotencyKey(String client, String idempotencyKey) {
        try {
            return emailMessageRepo.findByClientAndIdempotencyKey(client, idempotencyKey);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<EmailMessage> getRecent(EmailMessageStatus status, EmailType type, String client, int limit) {
        try {
            return emailMessageRepo.findRecent(status, type, client, PageRequest.of(0, limit));
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Map<EmailMessageStatus, Long> countByStatus() {
        try {
            Map<EmailMessageStatus, Long> counts = new EnumMap<>(EmailMessageStatus.class);
            for (EmailMessageStatus status : EmailMessageStatus.values()) {
                counts.put(status, 0L);
            }
            for (Object[] row : emailMessageRepo.countGroupedByStatus()) {
                counts.put((EmailMessageStatus) row[0], ((Number) row[1]).longValue());
            }
            return counts;
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public EmailMessage save(EmailMessage message) {
        try {
            message.setUpdatedAt(clock.instant());
            return emailMessageRepo.save(message);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public boolean insertIfAbsent(EmailMessage m) {
        try {
            return emailMessageRepo.insertIfAbsent(m.getId(), m.getClient(), m.getIdempotencyKey(), m.getRequestHash(),
                    m.getType().name(), m.getTemplateCode(), m.getToAddress(), m.getFromName(), m.getReplyTo(),
                    m.getSubject(), m.getHtml(), m.getStatus().name(), m.getNextAttemptAt(), m.getCreatedAt()) > 0;
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    @Transactional
    public List<EmailMessage> claimDue(int limit, Duration lock) {
        try {
            Instant now = clock.instant();
            List<EmailMessage> due = emailMessageRepo.lockDue(now, limit);
            for (EmailMessage message : due) {
                message.setStatus(EmailMessageStatus.SENDING);
                message.setLockedUntil(now.plus(lock));
                message.setAttempts(message.getAttempts() + 1);
                message.setUpdatedAt(now);
            }
            return emailMessageRepo.saveAll(due);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    @Transactional
    public int deleteSentBefore(Instant before) {
        try {
            return emailMessageRepo.deleteSentBefore(before);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
