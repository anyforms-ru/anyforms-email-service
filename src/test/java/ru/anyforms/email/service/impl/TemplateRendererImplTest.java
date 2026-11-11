package ru.anyforms.email.service.impl;

import org.junit.jupiter.api.Test;
import ru.anyforms.email.exception.TemplateRenderException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemplateRendererImplTest {

    private final TemplateRendererImpl renderer = new TemplateRendererImpl();

    @Test
    void escapesDoubleBracesAndKeepsTripleBracesRaw() {
        String html = renderer.renderHtml("<p>{{ name }}</p>{{{rows}}}",
                Map.of("name", "<Анна & Co>", "rows", "<tr><td>1</td></tr>"));

        assertEquals("<p>&lt;Анна &amp; Co&gt;</p><tr><td>1</td></tr>", html);
    }

    @Test
    void subjectIsNotEscapedAndHasNoLineBreaks() {
        String subject = renderer.renderSubject("Заказ #{{order}}\r\nготов", Map.of("order", "AB<12>"));

        assertEquals("Заказ #AB<12> готов", subject);
    }

    @Test
    void nullValueRendersEmptyAndNumbersAsText() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("missing", null);
        vars.put("total", 12000);

        assertEquals("[]12000", renderer.renderHtml("[{{missing}}]{{total}}", vars));
    }

    @Test
    void reportsEveryMissingVariableOnce() {
        TemplateRenderException e = assertThrows(TemplateRenderException.class,
                () -> renderer.renderHtml("{{a}} {{b}} {{a}} {{{c}}}", Map.of("b", "x")));

        assertEquals(List.of("a", "c"), e.getMissingVariables());
    }

    @Test
    void dollarAndBackslashInValuesAreLiteral() {
        assertEquals("$1 \\n", renderer.renderHtml("{{v}}", Map.of("v", "$1 \\n")));
    }

    @Test
    void listsVariablesAcrossSubjectAndHtml() {
        assertEquals(List.of("order", "name", "rows"),
                renderer.variables("Заказ {{order}}", "<p>{{name}}</p>{{{rows}}}{{order}}"));
    }
}
