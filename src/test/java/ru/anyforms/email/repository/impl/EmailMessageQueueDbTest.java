package ru.anyforms.email.repository.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.anyforms.email.config.ClockConfig;
import ru.anyforms.email.model.EmailMessage;
import ru.anyforms.email.model.EmailMessageStatus;
import ru.anyforms.email.model.EmailSender;
import ru.anyforms.email.model.EmailType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@EnabledIfSystemProperty(named = "emailDb", matches = "true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({EmailMessageManager.class, EmailSenderManager.class, ClockConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.datasource.url=${email.db.url:jdbc:postgresql://localhost:5475/email_test}",
        "spring.datasource.username=postgres",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
})
class EmailMessageQueueDbTest {

    @Autowired
    private EmailMessageManager manager;

    @Autowired
    private EmailSenderManager senderManager;

    @Autowired
    private EmailMessageRepo repo;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("delete from email_message");
    }

    private static EmailMessage message(String key, Instant nextAttemptAt) {
        Instant now = Instant.now();
        return EmailMessage.builder()
                .id(UUID.randomUUID())
                .client("anyforms-back")
                .idempotencyKey(key)
                .requestHash("h".repeat(64))
                .type(EmailType.SYSTEM)
                .toAddress("client@example.com")
                .subject("Тема")
                .html("<p/>")
                .status(EmailMessageStatus.QUEUED)
                .nextAttemptAt(nextAttemptAt)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private void insert(EmailMessage m) {
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> assertTrue(manager.insertIfAbsent(m)));
    }

    @Test
    void secondInsertWithSameKeyIsIgnored() {
        insert(message("task:1", Instant.now()));

        Boolean inserted = new TransactionTemplate(transactionManager)
                .execute(s -> manager.insertIfAbsent(message("task:1", Instant.now())));

        assertFalse(inserted);
        assertEquals(1, repo.count());
        assertTrue(manager.getByClientAndIdempotencyKey("anyforms-back", "task:1").isPresent());
    }

    @Test
    void claimsDueAndStaleButNotFutureLetters() {
        EmailMessage due = message("due", Instant.now().minusSeconds(5));
        EmailMessage future = message("future", Instant.now().plusSeconds(3600));
        EmailMessage stale = message("stale", Instant.now().minusSeconds(600));
        insert(due);
        insert(future);
        insert(stale);
        jdbcTemplate.update("update email_message set status = 'SENDING', locked_until = now() - interval '1 minute', attempts = 1 where idempotency_key = 'stale'");

        List<EmailMessage> claimed = manager.claimDue(10, Duration.ofMinutes(2));

        assertEquals(2, claimed.size());
        assertTrue(claimed.stream().allMatch(m -> m.getStatus() == EmailMessageStatus.SENDING));
        assertEquals(2, claimed.stream().filter(m -> m.getIdempotencyKey().equals("stale")).findFirst().orElseThrow().getAttempts());
        assertEquals(0, manager.claimDue(10, Duration.ofMinutes(2)).size());
    }

    @Test
    void lockedRowsAreSkippedByAnotherWorker() throws Exception {
        insert(message("a", Instant.now().minusSeconds(5)));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Integer> holder = CompletableFuture.supplyAsync(() ->
                new TransactionTemplate(transactionManager).execute(s -> {
                    int rows = repo.lockDue(Instant.now(), 10).size();
                    locked.countDown();
                    try {
                        release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return rows;
                }));
        assertTrue(locked.await(10, TimeUnit.SECONDS));

        assertEquals(0, manager.claimDue(10, Duration.ofMinutes(2)).size());
        release.countDown();
        assertEquals(1, holder.get(10, TimeUnit.SECONDS));
    }

    @Test
    void filtersAndStatsWorkWithNullParams() {
        insert(message("x", Instant.now()));
        insert(message("y", Instant.now()));

        assertEquals(2, manager.getRecent(null, null, null, 10).size());
        assertEquals(2, manager.getRecent(EmailMessageStatus.QUEUED, EmailType.SYSTEM, "anyforms-back", 10).size());
        assertEquals(0, manager.getRecent(null, EmailType.MARKETING, null, 10).size());
        assertEquals(2L, manager.countByStatus().get(EmailMessageStatus.QUEUED));
        assertEquals(0L, manager.countByStatus().get(EmailMessageStatus.FAILED));
    }

    @Test
    void deletesOnlyOldSentLetters() {
        insert(message("old", Instant.now()));
        insert(message("fresh", Instant.now()));
        jdbcTemplate.update("update email_message set status = 'SENT', sent_at = now() - interval '100 days' where idempotency_key = 'old'");
        jdbcTemplate.update("update email_message set status = 'SENT', sent_at = now() where idempotency_key = 'fresh'");

        assertEquals(1, manager.deleteSentBefore(Instant.now().minus(Duration.ofDays(90))));
        assertEquals(1, repo.count());
    }

    @Test
    void sendersAreSeeded() {
        List<EmailSender> senders = senderManager.getAll();

        assertEquals(List.of(EmailType.SYSTEM, EmailType.MARKETING), senders.stream().map(EmailSender::getType).toList());
        assertFalse(senders.get(0).isConfigured());
    }
}
