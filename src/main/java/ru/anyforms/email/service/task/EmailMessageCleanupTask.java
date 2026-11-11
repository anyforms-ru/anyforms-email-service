package ru.anyforms.email.service.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.anyforms.email.repository.EmailMessageCleaner;

import java.time.Clock;
import java.time.Duration;

@Slf4j
@Component
class EmailMessageCleanupTask {

    private final EmailMessageCleaner emailMessageCleaner;
    private final Clock clock;
    private final Duration retention;

    EmailMessageCleanupTask(EmailMessageCleaner emailMessageCleaner,
                            Clock clock,
                            @Value("${email.queue.retention-days}") long retentionDays) {
        this.emailMessageCleaner = emailMessageCleaner;
        this.clock = clock;
        this.retention = Duration.ofDays(retentionDays);
    }

    @Scheduled(cron = "${email.queue.cleanup-cron}", zone = "UTC")
    public void cleanup() {
        try {
            int deleted = emailMessageCleaner.deleteSentBefore(clock.instant().minus(retention));
            log.info("Удалено отправленных писем старше {} дней: {}", retention.toDays(), deleted);
        } catch (Exception e) {
            log.error("Не удалось почистить отправленные письма", e);
        }
    }
}
