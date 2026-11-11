package ru.anyforms.email.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailTemplateUpsertRequest(
        @Size(max = 1000, message = "Описание длиннее 1000 символов")
        String description,
        @NotBlank(message = "Укажите тему письма")
        @Size(max = 998, message = "Тема длиннее 998 символов")
        String subject,
        @NotBlank(message = "Загрузите HTML шаблона")
        @Size(max = 1_000_000, message = "HTML шаблона больше миллиона символов")
        String html
) {
}
