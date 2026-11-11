package ru.anyforms.email.dto;

import java.time.Instant;
import java.util.List;

public record EmailTemplateDTO(
        String code,
        String description,
        String subject,
        String html,
        List<String> variables,
        Instant createdAt,
        Instant updatedAt
) {
}
