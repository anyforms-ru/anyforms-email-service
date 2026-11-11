package ru.anyforms.email.dto;

import java.time.Instant;
import java.util.List;

public record EmailTemplateSummaryDTO(
        String code,
        String description,
        String subject,
        List<String> variables,
        Instant updatedAt
) {
}
