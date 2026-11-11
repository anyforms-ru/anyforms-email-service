package ru.anyforms.email.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.email.dto.SendEmailRequest;
import ru.anyforms.email.dto.SendEmailResponse;
import ru.anyforms.email.dto.SenderDTO;
import ru.anyforms.email.dto.SenderUpdateRequest;
import ru.anyforms.email.model.EmailSender;
import ru.anyforms.email.model.EmailType;
import ru.anyforms.email.repository.GetterEmailSender;
import ru.anyforms.email.repository.SaverEmailSender;
import ru.anyforms.email.service.EmailQueueService;
import ru.anyforms.email.service.EmailSenderService;
import ru.anyforms.email.service.provider.EmailProvider;
import ru.anyforms.email.service.provider.EmailProviderRegistry;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
class EmailSenderServiceImpl implements EmailSenderService {

    static final String ADMIN_CLIENT = "email-service-admin";

    private final GetterEmailSender getterEmailSender;
    private final SaverEmailSender saverEmailSender;
    private final EmailProviderRegistry emailProviderRegistry;
    private final EmailQueueService emailQueueService;

    EmailSenderServiceImpl(GetterEmailSender getterEmailSender,
                           SaverEmailSender saverEmailSender,
                           EmailProviderRegistry emailProviderRegistry,
                           EmailQueueService emailQueueService) {
        this.getterEmailSender = getterEmailSender;
        this.saverEmailSender = saverEmailSender;
        this.emailProviderRegistry = emailProviderRegistry;
        this.emailQueueService = emailQueueService;
    }

    @Override
    public List<SenderDTO> list() {
        return getterEmailSender.getAll().stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public SenderDTO update(EmailType type, SenderUpdateRequest request) {
        EmailSender sender = find(type);
        sender.setFromAddress(request.fromAddress().trim());
        sender.setFromName(blankToNull(request.fromName()));
        sender.setReplyTo(blankToNull(request.replyTo()));
        SenderDTO saved = toDto(saverEmailSender.save(sender));
        log.info("Отправитель писем {} изменён", type);
        return saved;
    }

    @Override
    public SendEmailResponse sendTest(EmailType type, String to) {
        EmailSender sender = find(type);
        if (!sender.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Сначала укажите адрес отправителя для писем " + type);
        }
        String html = "<p>Тестовое письмо email-service anyforms.</p><p>Тип: " + type
                + ", отправитель: " + sender.getFromAddress() + ".</p>";
        SendEmailRequest request = new SendEmailRequest(type, to, null, null, null, null,
                "Тестовое письмо anyforms (" + type + ")", html);
        return emailQueueService.enqueue(ADMIN_CLIENT, "test:" + UUID.randomUUID(), request);
    }

    private EmailSender find(EmailType type) {
        return getterEmailSender.getByType(type)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Отправитель не найден: " + type));
    }

    private SenderDTO toDto(EmailSender sender) {
        EmailProvider provider = emailProviderRegistry.get(sender.getType());
        return new SenderDTO(sender.getType(), provider.name(), provider.credentialsConfigured(),
                sender.getFromAddress(), sender.getFromName(), sender.getReplyTo(),
                sender.isConfigured() && provider.credentialsConfigured(), sender.getUpdatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
