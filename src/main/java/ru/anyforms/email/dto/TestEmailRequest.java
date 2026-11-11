package ru.anyforms.email.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record TestEmailRequest(
        @NotBlank(message = "Укажите адрес для тестового письма")
        @Email(message = "Некорректный адрес для тестового письма")
        String to
) {
}
