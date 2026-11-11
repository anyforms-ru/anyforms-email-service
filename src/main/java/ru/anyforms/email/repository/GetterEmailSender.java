package ru.anyforms.email.repository;

import ru.anyforms.email.model.EmailSender;
import ru.anyforms.email.model.EmailType;

import java.util.List;
import java.util.Optional;

public interface GetterEmailSender {
    Optional<EmailSender> getByType(EmailType type);

    List<EmailSender> getAll();
}
