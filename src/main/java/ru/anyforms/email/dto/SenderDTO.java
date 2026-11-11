package ru.anyforms.email.dto;

import ru.anyforms.email.model.EmailType;

import java.time.Instant;

public record SenderDTO(
        EmailType type,
        String provider,
        boolean providerConfigured,
        String fromAddress,
        String fromName,
        String replyTo,
        boolean ready,
        Instant updatedAt
) {
}
