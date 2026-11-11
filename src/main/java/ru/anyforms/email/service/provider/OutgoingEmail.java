package ru.anyforms.email.service.provider;

import java.util.UUID;

public record OutgoingEmail(
        UUID id,
        String to,
        String fromAddress,
        String fromName,
        String replyTo,
        String subject,
        String html
) {
}
