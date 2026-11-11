package ru.anyforms.email.dto;

import java.util.Map;

public record TemplatePreviewRequest(Map<String, Object> variables) {
}
