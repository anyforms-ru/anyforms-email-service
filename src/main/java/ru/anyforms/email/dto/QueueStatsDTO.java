package ru.anyforms.email.dto;

import ru.anyforms.email.model.EmailMessageStatus;

import java.util.Map;

public record QueueStatsDTO(Map<EmailMessageStatus, Long> byStatus) {
}
