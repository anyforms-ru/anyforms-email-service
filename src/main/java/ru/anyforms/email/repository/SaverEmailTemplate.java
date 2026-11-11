package ru.anyforms.email.repository;

import ru.anyforms.email.model.EmailTemplate;

public interface SaverEmailTemplate {
    EmailTemplate save(EmailTemplate template);
}
