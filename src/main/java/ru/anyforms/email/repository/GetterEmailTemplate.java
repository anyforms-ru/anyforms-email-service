package ru.anyforms.email.repository;

import ru.anyforms.email.model.EmailTemplate;

import java.util.List;
import java.util.Optional;

public interface GetterEmailTemplate {
    Optional<EmailTemplate> getByCode(String code);

    List<EmailTemplate> getAll();
}
