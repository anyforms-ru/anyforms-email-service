package ru.anyforms.email.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.anyforms.email.dto.EmailTemplateDTO;
import ru.anyforms.email.dto.EmailTemplateSummaryDTO;
import ru.anyforms.email.dto.EmailTemplateUpsertRequest;
import ru.anyforms.email.dto.RenderedEmailDTO;
import ru.anyforms.email.dto.TemplatePreviewRequest;
import ru.anyforms.email.service.EmailTemplateService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
@Tag(name = "Templates", description = "Шаблоны писем: {{переменная}} экранируется, {{{переменная}}} вставляется как есть")
public class EmailTemplateController {

    private final EmailTemplateService emailTemplateService;

    @Operation(summary = "Список шаблонов")
    @GetMapping
    public ResponseEntity<List<EmailTemplateSummaryDTO>> list() {
        return ResponseEntity.ok(emailTemplateService.list());
    }

    @Operation(summary = "Шаблон целиком")
    @GetMapping("/{code}")
    public ResponseEntity<EmailTemplateDTO> get(@PathVariable String code) {
        return ResponseEntity.ok(emailTemplateService.get(code));
    }

    @Operation(summary = "Загрузить или заменить шаблон")
    @PutMapping("/{code}")
    public ResponseEntity<EmailTemplateDTO> upsert(@PathVariable String code,
                                                   @Valid @RequestBody EmailTemplateUpsertRequest request) {
        return ResponseEntity.ok(emailTemplateService.upsert(code, request));
    }

    @Operation(summary = "Удалить шаблон")
    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        emailTemplateService.delete(code);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Отрендерить шаблон с переменными, ничего не отправляя")
    @PostMapping("/{code}/preview")
    public ResponseEntity<RenderedEmailDTO> preview(@PathVariable String code,
                                                    @RequestBody(required = false) TemplatePreviewRequest request) {
        return ResponseEntity.ok(emailTemplateService.render(code, request == null ? null : request.variables()));
    }
}
