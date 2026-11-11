package ru.anyforms.email.repository;

import ru.anyforms.email.model.EmailSender;

public interface SaverEmailSender {
    EmailSender save(EmailSender sender);
}
