package ru.anyforms.email.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.email.dto.EmailTemplateDTO;
import ru.anyforms.email.dto.EmailTemplateSummaryDTO;
import ru.anyforms.email.dto.EmailTemplateUpsertRequest;
import ru.anyforms.email.dto.RenderedEmailDTO;
import ru.anyforms.email.model.EmailTemplate;
import ru.anyforms.email.repository.EmailTemplateDeleter;
import ru.anyforms.email.repository.GetterEmailTemplate;
import ru.anyforms.email.repository.SaverEmailTemplate;
import ru.anyforms.email.service.EmailTemplateService;
import ru.anyforms.email.service.TemplateRenderer;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
class EmailTemplateServiceImpl implements EmailTemplateService {

    static final Pattern CODE_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{0,99}");

    private final GetterEmailTemplate getterEmailTemplate;
    private final SaverEmailTemplate saverEmailTemplate;
    private final EmailTemplateDeleter emailTemplateDeleter;
    private final TemplateRenderer templateRenderer;

    EmailTemplateServiceImpl(GetterEmailTemplate getterEmailTemplate,
                             SaverEmailTemplate saverEmailTemplate,
                             EmailTemplateDeleter emailTemplateDeleter,
                             TemplateRenderer templateRenderer) {
        this.getterEmailTemplate = getterEmailTemplate;
        this.saverEmailTemplate = saverEmailTemplate;
        this.emailTemplateDeleter = emailTemplateDeleter;
        this.templateRenderer = templateRenderer;
    }

    @Override
    public List<EmailTemplateSummaryDTO> list() {
        return getterEmailTemplate.getAll().stream()
                .map(t -> new EmailTemplateSummaryDTO(t.getCode(), t.getDescription(), t.getSubject(),
                        templateRenderer.variables(t.getSubject(), t.getHtml()), t.getUpdatedAt()))
                .toList();
    }

    @Override
    public EmailTemplateDTO get(String code) {
        return toDto(find(code));
    }

    @Override
    @Transactional
    public EmailTemplateDTO upsert(String code, EmailTemplateUpsertRequest request) {
        validateCode(code);
        EmailTemplate template = getterEmailTemplate.getByCode(code)
                .orElseGet(() -> EmailTemplate.builder().code(code).build());
        template.setDescription(request.description() == null || request.description().isBlank()
                ? null
                : request.description().trim());
        template.setSubject(request.subject().trim());
        template.setHtml(request.html());
        EmailTemplate saved = saverEmailTemplate.save(template);
        log.info("Шаблон письма {} сохранён", code);
        return toDto(saved);
    }

    @Override
    @Transactional
    public void delete(String code) {
        if (!emailTemplateDeleter.delete(code)) {
            throw notFound(code);
        }
        log.info("Шаблон письма {} удалён", code);
    }

    @Override
    public RenderedEmailDTO render(String code, Map<String, Object> variables) {
        EmailTemplate template = find(code);
        return new RenderedEmailDTO(
                templateRenderer.renderSubject(template.getSubject(), variables),
                templateRenderer.renderHtml(template.getHtml(), variables));
    }

    private EmailTemplate find(String code) {
        return getterEmailTemplate.getByCode(code).orElseThrow(() -> notFound(code));
    }

    private EmailTemplateDTO toDto(EmailTemplate t) {
        return new EmailTemplateDTO(t.getCode(), t.getDescription(), t.getSubject(), t.getHtml(),
                templateRenderer.variables(t.getSubject(), t.getHtml()), t.getCreatedAt(), t.getUpdatedAt());
    }

    private static void validateCode(String code) {
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Код шаблона: латиница в нижнем регистре, цифры, точка, дефис, подчёркивание; до 100 символов");
        }
    }

    private static ResponseStatusException notFound(String code) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Шаблон письма не найден: " + code);
    }
}
