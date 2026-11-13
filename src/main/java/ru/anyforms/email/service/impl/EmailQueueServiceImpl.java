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
import ru.anyforms.email.dto.BulkEmailRequest;
import ru.anyforms.email.dto.BulkEmailResponse;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
class EmailQueueServiceImpl implements EmailQueueService {

    static final Pattern CLIENT_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{0,99}");
    static final int MAX_KEY_LENGTH = 255;
    static final int MAX_BULK_RECIPIENTS = 2000;
    static final Pattern RECIPIENT_SEPARATOR = Pattern.compile("[\\s,;]+");
    static final Pattern EMAIL_PATTERN = Pattern.compile("[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+");
    private static final int MAX_EMAIL_LENGTH = 320;
    private static final int MAX_INVALID_IN_MESSAGE = 20;

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

    @Override
    @Transactional
    public BulkEmailResponse enqueueBulk(String client, BulkEmailRequest request) {
        validateClient(client);
        String batchKey = request.batchKey().trim();
        Map<String, String> recipients = parseRecipients(request.recipients());
        String templateCode = request.templateCode().trim();
        String fromName = blankToNull(request.fromName());
        String replyTo = blankToNull(request.replyTo());

        RenderedEmailDTO rendered = emailTemplateService.render(templateCode, request.variables());
        Instant now = clock.instant();
        int queued = 0;
        for (String to : recipients.values()) {
            String key = bulkKey(batchKey, to);
            String hash = hash(new SendEmailRequest(request.type(), to.toLowerCase(Locale.ROOT), fromName, replyTo, templateCode,
                    request.variables(), null, null));
            EmailMessage message = EmailMessage.builder()
                    .id(UUID.randomUUID())
                    .client(client)
                    .idempotencyKey(key)
                    .requestHash(hash)
                    .type(request.type())
                    .templateCode(templateCode)
                    .toAddress(to)
                    .fromName(fromName)
                    .replyTo(replyTo)
                    .subject(rendered.subject())
                    .html(rendered.html())
                    .status(EmailMessageStatus.QUEUED)
                    .nextAttemptAt(now)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            if (saverEmailMessage.insertIfAbsent(message)) {
                queued++;
                continue;
            }
            EmailMessage existing = getterEmailMessage.getByClientAndIdempotencyKey(client, key)
                    .orElseThrow(() -> new IllegalStateException("Письмо с ключом идемпотентности не найдено после конфликта"));
            if (!existing.getRequestHash().equals(hash)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Рассылка " + batchKey + " уже поставлена с другим шаблоном или переменными (" + to
                                + "). Для новой рассылки нужен новый batchKey");
            }
        }
        int duplicates = recipients.size() - queued;
        log.info("Рассылка {} от клиента {}: шаблон {}, тип {}, адресов {}, поставлено {}, уже были {}",
                batchKey, client, templateCode, request.type(), recipients.size(), queued, duplicates);
        return new BulkEmailResponse(batchKey, recipients.size(), queued, duplicates);
    }

    /** Адреса без повторов (без учёта регистра) в исходном порядке: ключ — адрес в нижнем регистре. */
    static Map<String, String> parseRecipients(List<String> raw) {
        Map<String, String> unique = new LinkedHashMap<>();
        List<String> invalid = new ArrayList<>();
        for (String line : raw) {
            if (line == null) {
                continue;
            }
            for (String token : RECIPIENT_SEPARATOR.split(line.trim())) {
                String address = token.trim();
                if (address.isEmpty()) {
                    continue;
                }
                if (address.length() > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matcher(address).matches()) {
                    invalid.add(address);
                    continue;
                }
                unique.putIfAbsent(address.toLowerCase(Locale.ROOT), address);
            }
        }
        if (!invalid.isEmpty()) {
            String shown = String.join(", ", invalid.subList(0, Math.min(invalid.size(), MAX_INVALID_IN_MESSAGE)));
            String more = invalid.size() > MAX_INVALID_IN_MESSAGE ? " и ещё " + (invalid.size() - MAX_INVALID_IN_MESSAGE) : "";
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Некорректные адреса (" + invalid.size() + "): " + shown + more + ". Ничего не отправлено");
        }
        if (unique.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите хотя бы один адрес получателя");
        }
        if (unique.size() > MAX_BULK_RECIPIENTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "За раз можно не больше " + MAX_BULK_RECIPIENTS + " адресов, передано " + unique.size());
        }
        return unique;
    }

    /** Ключ письма рассылки: batchKey + адрес; длинный адрес заменяется хэшем, чтобы ключ влез в 255 символов. */
    static String bulkKey(String batchKey, String to) {
        String address = to.toLowerCase(Locale.ROOT);
        String key = "bulk:" + batchKey + ":" + address;
        if (key.length() <= MAX_KEY_LENGTH) {
            return key;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(address.getBytes(StandardCharsets.UTF_8));
            return "bulk:" + batchKey + ":#" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Не получилось посчитать хэш адреса", e);
        }
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
