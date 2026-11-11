package ru.anyforms.email.repository.impl;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.anyforms.email.model.EmailMessage;
import ru.anyforms.email.model.EmailMessageStatus;
import ru.anyforms.email.model.EmailType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface EmailMessageRepo extends JpaRepository<EmailMessage, UUID> {

    Optional<EmailMessage> findByClientAndIdempotencyKey(String client, String idempotencyKey);

    @Modifying
    @Query(value = """
            insert into email_message (id, client, idempotency_key, request_hash, type, template_code,
                                       to_address, from_name, reply_to, subject, html, status, attempts,
                                       next_attempt_at, created_at, updated_at)
            values (:id, :client, :idempotencyKey, :requestHash, :type, :templateCode,
                    :toAddress, :fromName, :replyTo, :subject, :html, :status, 0,
                    :nextAttemptAt, :createdAt, :createdAt)
            on conflict (client, idempotency_key) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("client") String client,
                       @Param("idempotencyKey") String idempotencyKey,
                       @Param("requestHash") String requestHash,
                       @Param("type") String type,
                       @Param("templateCode") String templateCode,
                       @Param("toAddress") String toAddress,
                       @Param("fromName") String fromName,
                       @Param("replyTo") String replyTo,
                       @Param("subject") String subject,
                       @Param("html") String html,
                       @Param("status") String status,
                       @Param("nextAttemptAt") Instant nextAttemptAt,
                       @Param("createdAt") Instant createdAt);

    @Query(value = """
            select * from email_message
            where (status = 'QUEUED' and next_attempt_at <= :now)
               or (status = 'SENDING' and locked_until < :now)
            order by next_attempt_at
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<EmailMessage> lockDue(@Param("now") Instant now, @Param("limit") int limit);

    @Query("""
            select m from EmailMessage m
            where (:status is null or m.status = :status)
              and (:type is null or m.type = :type)
              and (:client is null or m.client = :client)
            order by m.createdAt desc
            """)
    List<EmailMessage> findRecent(@Param("status") EmailMessageStatus status,
                                  @Param("type") EmailType type,
                                  @Param("client") String client,
                                  Pageable pageable);

    @Query("select m.status, count(m) from EmailMessage m group by m.status")
    List<Object[]> countGroupedByStatus();

    @Modifying
    @Query("delete from EmailMessage m where m.status = ru.anyforms.email.model.EmailMessageStatus.SENT and m.sentAt < :before")
    int deleteSentBefore(@Param("before") Instant before);
}
