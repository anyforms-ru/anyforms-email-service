package ru.anyforms.email.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.anyforms.email.dto.BulkEmailRequest;
import ru.anyforms.email.dto.BulkEmailResponse;
import ru.anyforms.email.dto.EmailMessageDTO;
import ru.anyforms.email.dto.SendEmailRequest;
import ru.anyforms.email.dto.SendEmailResponse;
import ru.anyforms.email.service.EmailMessageAdminService;
import ru.anyforms.email.service.EmailQueueService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/emails")
@RequiredArgsConstructor
@Tag(name = "Emails", description = "Постановка писем в очередь")
public class EmailController {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    public static final String CLIENT_HEADER = "X-Client-Id";

    private final EmailQueueService emailQueueService;
    private final EmailMessageAdminService emailMessageAdminService;

    @Operation(summary = "Поставить письмо в очередь",
            description = "200 — письмо закоммичено в очередь. Повтор с тем же Idempotency-Key и тем же телом "
                    + "возвращает то же письмо (duplicate=true), с другим телом — 409")
    @PostMapping
    public ResponseEntity<SendEmailResponse> send(@RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
                                                  @RequestHeader(CLIENT_HEADER) String client,
                                                  @Valid @RequestBody SendEmailRequest request) {
        return ResponseEntity.ok(emailQueueService.enqueue(client, idempotencyKey, request));
    }

    @Operation(summary = "Рассылка шаблона по списку адресов",
            description = "Все письма ставятся в очередь одной транзакцией: при ошибке (кривой адрес, нет шаблона "
                    + "или переменной) не ставится ни одно. Отправляет обычный воркер очереди. Повтор с тем же "
                    + "batchKey не дублирует письма; тот же batchKey с другим шаблоном или переменными — 409")
    @PostMapping("/bulk")
    public ResponseEntity<BulkEmailResponse> sendBulk(@RequestHeader(CLIENT_HEADER) String client,
                                                      @Valid @RequestBody BulkEmailRequest request) {
        return ResponseEntity.ok(emailQueueService.enqueueBulk(client, request));
    }

    @Operation(summary = "Статус письма")
    @GetMapping("/{id}")
    public ResponseEntity<EmailMessageDTO> get(@PathVariable UUID id) {
        return ResponseEntity.ok(emailMessageAdminService.get(id).message());
    }
}
