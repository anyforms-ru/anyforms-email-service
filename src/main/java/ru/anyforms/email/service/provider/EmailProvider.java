package ru.anyforms.email.service.provider;

import ru.anyforms.email.model.EmailType;

public interface EmailProvider {

    EmailType type();

    String name();

    boolean credentialsConfigured();

    String send(OutgoingEmail email);
}
