package ru.anyforms.email.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.anyforms.email.model.EmailTemplate;
import ru.anyforms.email.repository.EmailTemplateDeleter;
import ru.anyforms.email.repository.GetterEmailTemplate;
import ru.anyforms.email.repository.SaverEmailTemplate;

import java.util.List;
import java.util.Optional;

@Component
@AllArgsConstructor
@Log4j2
class EmailTemplateManager implements GetterEmailTemplate, SaverEmailTemplate, EmailTemplateDeleter {

    private final EmailTemplateRepo emailTemplateRepo;

    @Override
    public Optional<EmailTemplate> getByCode(String code) {
        try {
            return emailTemplateRepo.findById(code);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<EmailTemplate> getAll() {
        try {
            return emailTemplateRepo.findAllByOrderByCodeAsc();
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public EmailTemplate save(EmailTemplate template) {
        try {
            return emailTemplateRepo.save(template);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public boolean delete(String code) {
        try {
            if (!emailTemplateRepo.existsById(code)) {
                return false;
            }
            emailTemplateRepo.deleteById(code);
            return true;
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
