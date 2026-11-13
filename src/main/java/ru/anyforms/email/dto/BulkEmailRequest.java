package ru.anyforms.email.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.anyforms.email.model.EmailType;

import java.util.List;
import java.util.Map;

/**
 * Рассылка одного шаблона по списку адресов. Каждая строка {@code recipients} может содержать
 * несколько адресов через запятую, точку с запятой, пробел или перевод строки.
 */
public record BulkEmailRequest(
        @NotBlank(message = "Укажите batchKey — ключ рассылки, по нему повтор не задублирует письма")
        @Size(max = 100, message = "batchKey длиннее 100 символов")
        String batchKey,
        @NotNull(message = "Укажите тип письма: SYSTEM или MARKETING")
        EmailType type,
        @NotBlank(message = "Выберите шаблон")
        @Size(max = 100, message = "Код шаблона длиннее 100 символов")
        String templateCode,
        Map<String, Object> variables,
        @Size(max = 255, message = "Имя отправителя длиннее 255 символов")
        String fromName,
        @Email(message = "Некорректный адрес Reply-To")
        @Size(max = 320, message = "Reply-To длиннее 320 символов")
        String replyTo,
        @NotEmpty(message = "Укажите получателей")
        List<String> recipients
) {
}
