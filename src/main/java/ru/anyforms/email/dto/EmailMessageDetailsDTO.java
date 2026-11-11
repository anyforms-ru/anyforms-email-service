package ru.anyforms.email.dto;

import ru.anyforms.email.model.EmailMessage;

public record EmailMessageDetailsDTO(EmailMessageDTO message, String html) {
    public static EmailMessageDetailsDTO from(EmailMessage m) {
        return new EmailMessageDetailsDTO(EmailMessageDTO.from(m), m.getHtml());
    }
}
