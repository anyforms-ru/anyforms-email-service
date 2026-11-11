package ru.anyforms.email.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.anyforms.email.model.EmailType;

import java.util.Map;

public record SendEmailRequest(
        @NotNull(message = "Укажите тип письма: SYSTEM или MARKETING")
        EmailType type,
        @NotBlank(message = "Укажите получателя")
        @Email(message = "Некорректный адрес получателя")
        @Size(max = 320, message = "Адрес получателя длиннее 320 символов")
        String to,
        @Size(max = 255, message = "Имя отправителя длиннее 255 символов")
        String fromName,
        @Email(message = "Некорректный адрес Reply-To")
        @Size(max = 320, message = "Reply-To длиннее 320 символов")
        String replyTo,
        @Size(max = 100, message = "Код шаблона длиннее 100 символов")
        String templateCode,
        Map<String, Object> variables,
        @Size(max = 998, message = "Тема длиннее 998 символов")
        String subject,
        String html
) {
}
