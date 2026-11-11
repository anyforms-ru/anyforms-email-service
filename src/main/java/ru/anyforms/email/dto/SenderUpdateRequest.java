package ru.anyforms.email.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SenderUpdateRequest(
        @NotBlank(message = "Укажите адрес отправителя")
        @Email(message = "Некорректный адрес отправителя")
        @Size(max = 320, message = "Адрес отправителя длиннее 320 символов")
        String fromAddress,
        @Size(max = 255, message = "Имя отправителя длиннее 255 символов")
        String fromName,
        @Email(message = "Некорректный адрес Reply-To")
        @Size(max = 320, message = "Reply-To длиннее 320 символов")
        String replyTo
) {
}
