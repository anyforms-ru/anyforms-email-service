package ru.anyforms.email.repository;

import ru.anyforms.email.model.EmailMessage;

public interface SaverEmailMessage {
    EmailMessage save(EmailMessage message);

    boolean insertIfAbsent(EmailMessage message);
}
