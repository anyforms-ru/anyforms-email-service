package ru.anyforms.email.service;

import ru.anyforms.email.dto.EmailTemplateDTO;
import ru.anyforms.email.dto.EmailTemplateSummaryDTO;
import ru.anyforms.email.dto.EmailTemplateUpsertRequest;
import ru.anyforms.email.dto.RenderedEmailDTO;

import java.util.List;
import java.util.Map;

public interface EmailTemplateService {

    List<EmailTemplateSummaryDTO> list();

    EmailTemplateDTO get(String code);

    EmailTemplateDTO upsert(String code, EmailTemplateUpsertRequest request);

    void delete(String code);

    RenderedEmailDTO render(String code, Map<String, Object> variables);
}
