package ru.anyforms.email.dto;

import ru.anyforms.email.model.EmailMessageStatus;

import java.util.UUID;

public record SendEmailResponse(UUID id, EmailMessageStatus status, boolean duplicate) {
}
