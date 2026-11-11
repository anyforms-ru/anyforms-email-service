package ru.anyforms.email.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.anyforms.email.dto.SendEmailResponse;
import ru.anyforms.email.dto.SenderDTO;
import ru.anyforms.email.dto.SenderUpdateRequest;
import ru.anyforms.email.dto.TestEmailRequest;
import ru.anyforms.email.model.EmailType;
import ru.anyforms.email.service.EmailSenderService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/senders")
@RequiredArgsConstructor
@Tag(name = "Senders", description = "Адреса отправителей системных и маркетинговых писем")
public class EmailSenderController {

    private final EmailSenderService emailSenderService;

    @Operation(summary = "Отправители и готовность провайдеров")
    @GetMapping
    public ResponseEntity<List<SenderDTO>> list() {
        return ResponseEntity.ok(emailSenderService.list());
    }

    @Operation(summary = "Изменить отправителя")
    @PutMapping("/{type}")
    public ResponseEntity<SenderDTO> update(@PathVariable EmailType type,
                                            @Valid @RequestBody SenderUpdateRequest request) {
        return ResponseEntity.ok(emailSenderService.update(type, request));
    }

    @Operation(summary = "Поставить в очередь тестовое письмо")
    @PostMapping("/{type}/test")
    public ResponseEntity<SendEmailResponse> test(@PathVariable EmailType type,
                                                  @Valid @RequestBody TestEmailRequest request) {
        return ResponseEntity.ok(emailSenderService.sendTest(type, request.to().trim()));
    }
}
