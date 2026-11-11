package ru.anyforms.email.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.email.dto.RenderedEmailDTO;
import ru.anyforms.email.dto.SendEmailRequest;
import ru.anyforms.email.dto.SendEmailResponse;
import ru.anyforms.email.model.EmailMessage;
import ru.anyforms.email.model.EmailMessageStatus;
import ru.anyforms.email.repository.GetterEmailMessage;
import ru.anyforms.email.repository.SaverEmailMessage;
import ru.anyforms.email.service.EmailQueueService;
import ru.anyforms.email.service.EmailTemplateService;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
class EmailQueueServiceImpl implements EmailQueueService {

    static final Pattern CLIENT_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{0,99}");
    static final int MAX_KEY_LENGTH = 255;

    private final GetterEmailMessage getterEmailMessage;
    private final SaverEmailMessage saverEmailMessage;
    private final EmailTemplateService emailTemplateService;
    private final Clock clock;
    private final ObjectMapper canonicalMapper = JsonMapper.builder()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .build();

    EmailQueueServiceImpl(GetterEmailMessage getterEmailMessage,
                          SaverEmailMessage saverEmailMessage,
                          EmailTemplateService emailTemplateService,
                          Clock clock) {
        this.getterEmailMessage = getterEmailMessage;
        this.saverEmailMessage = saverEmailMessage;
        this.emailTemplateService = emailTemplateService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SendEmailResponse enqueue(String client, String idempotencyKey, SendEmailRequest request) {
        validateClient(client);
        validateKey(idempotencyKey);
        validateContent(request);
        String hash = hash(request);

        var existing = getterEmailMessage.getByClientAndIdempotencyKey(client, idempotencyKey);
        if (existing.isPresent()) {
            return duplicate(existing.get(), hash);
        }

        RenderedEmailDTO rendered = render(request);
        Instant now = clock.instant();
        EmailMessage message = EmailMessage.builder()
                .id(UUID.randomUUID())
                .client(client)
                .idempotencyKey(idempotencyKey)
                .requestHash(hash)
                .type(request.type())
                .templateCode(blankToNull(request.templateCode()))
                .toAddress(request.to().trim())
                .fromName(blankToNull(request.fromName()))
                .replyTo(blankToNull(request.replyTo()))
                .subject(rendered.subject())
                .html(rendered.html())
                .status(EmailMessageStatus.QUEUED)
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        if (!saverEmailMessage.insertIfAbsent(message)) {
            EmailMessage concurrent = getterEmailMessage.getByClientAndIdempotencyKey(client, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Письмо с ключом идемпотентности не найдено после конфликта"));
            return duplicate(concurrent, hash);
        }
        log.info("Письмо {} поставлено в очередь: клиент {}, тип {}, шаблон {}",
                message.getId(), client, message.getType(), message.getTemplateCode());
        return new SendEmailResponse(message.getId(), message.getStatus(), false);
    }

    private SendEmailResponse duplicate(EmailMessage existing, String hash) {
        if (!existing.getRequestHash().equals(hash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ключ идемпотентности уже использован для другого письма: " + existing.getId());
        }
        log.info("Повторный запрос письма {} от клиента {}: в очередь не добавляем", existing.getId(), existing.getClient());
        return new SendEmailResponse(existing.getId(), existing.getStatus(), true);
    }

    private RenderedEmailDTO render(SendEmailRequest request) {
        if (request.templateCode() != null && !request.templateCode().isBlank()) {
            return emailTemplateService.render(request.templateCode().trim(), request.variables());
        }
        return new RenderedEmailDTO(request.subject().replaceAll("[\\r\\n]+", " ").trim(), request.html());
    }

    private static void validateClient(String client) {
        if (client == null || !CLIENT_PATTERN.matcher(client).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Заголовок X-Client-Id: латиница в нижнем регистре, цифры, точка, дефис, подчёркивание; до 100 символов");
        }
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Заголовок Idempotency-Key обязателен и не длиннее " + MAX_KEY_LENGTH + " символов");
        }
    }

    private static void validateContent(SendEmailRequest request) {
        boolean template = request.templateCode() != null && !request.templateCode().isBlank();
        boolean raw = request.subject() != null && !request.subject().isBlank()
                && request.html() != null && !request.html().isBlank();
        boolean partialRaw = (request.subject() != null && !request.subject().isBlank())
                || (request.html() != null && !request.html().isBlank());
        if (template && partialRaw) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Передайте либо templateCode с variables, либо subject и html — не всё сразу");
        }
        if (!template && !raw) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Передайте templateCode с variables или subject и html");
        }
    }

    private String hash(SendEmailRequest request) {
        try {
            byte[] json = canonicalMapper.writeValueAsBytes(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Не получилось посчитать хэш запроса", e);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
