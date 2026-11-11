package ru.anyforms.email.service;

import java.util.List;
import java.util.Map;

public interface TemplateRenderer {

    String renderHtml(String template, Map<String, Object> variables);

    String renderSubject(String template, Map<String, Object> variables);

    List<String> variables(String... templates);
}
