package ru.anyforms.email.exception;

import java.util.List;

public class TemplateRenderException extends RuntimeException {

    private final List<String> missingVariables;

    public TemplateRenderException(List<String> missingVariables) {
        super("Не переданы переменные шаблона: " + String.join(", ", missingVariables));
        this.missingVariables = List.copyOf(missingVariables);
    }

    public List<String> getMissingVariables() {
        return missingVariables;
    }
}
