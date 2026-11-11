package ru.anyforms.email.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.anyforms.email.model.EmailSender;
import ru.anyforms.email.model.EmailType;
import ru.anyforms.email.repository.GetterEmailSender;
import ru.anyforms.email.repository.SaverEmailSender;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
@AllArgsConstructor
@Log4j2
class EmailSenderManager implements GetterEmailSender, SaverEmailSender {

    private final EmailSenderRepo emailSenderRepo;

    @Override
    public Optional<EmailSender> getByType(EmailType type) {
        try {
            return emailSenderRepo.findById(type);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<EmailSender> getAll() {
        try {
            return emailSenderRepo.findAll().stream()
                    .sorted(Comparator.comparing(EmailSender::getType))
                    .toList();
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public EmailSender save(EmailSender sender) {
        try {
            return emailSenderRepo.save(sender);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
