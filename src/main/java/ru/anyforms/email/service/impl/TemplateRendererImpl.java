package ru.anyforms.email.service.impl;

import org.springframework.stereotype.Component;
import ru.anyforms.email.exception.TemplateRenderException;
import ru.anyforms.email.service.TemplateRenderer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
class TemplateRendererImpl implements TemplateRenderer {

    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}}|\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}");

    @Override
    public String renderHtml(String template, Map<String, Object> variables) {
        return render(template, variables, true);
    }

    @Override
    public String renderSubject(String template, Map<String, Object> variables) {
        return render(template, variables, false).replaceAll("[\\r\\n]+", " ").trim();
    }

    @Override
    public List<String> variables(String... templates) {
        Set<String> names = new LinkedHashSet<>();
        for (String template : templates) {
            if (template == null) {
                continue;
            }
            Matcher matcher = PLACEHOLDER.matcher(template);
            while (matcher.find()) {
                names.add(name(matcher));
            }
        }
        return List.copyOf(names);
    }

    private String render(String template, Map<String, Object> variables, boolean escapeHtml) {
        Map<String, Object> values = variables == null ? Map.of() : variables;
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder(template.length());
        List<String> missing = new ArrayList<>();
        while (matcher.find()) {
            String name = name(matcher);
            boolean raw = matcher.group(1) != null;
            String replacement;
            if (!values.containsKey(name)) {
                if (!missing.contains(name)) {
                    missing.add(name);
                }
                replacement = "";
            } else {
                Object value = values.get(name);
                String text = value == null ? "" : String.valueOf(value);
                replacement = escapeHtml && !raw ? escape(text) : text;
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        if (!missing.isEmpty()) {
            throw new TemplateRenderException(missing);
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String name(Matcher matcher) {
        return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
