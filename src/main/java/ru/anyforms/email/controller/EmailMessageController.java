package ru.anyforms.email.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.anyforms.email.dto.EmailMessageDTO;
import ru.anyforms.email.dto.EmailMessageDetailsDTO;
import ru.anyforms.email.dto.QueueStatsDTO;
import ru.anyforms.email.model.EmailMessageStatus;
import ru.anyforms.email.model.EmailType;
import ru.anyforms.email.service.EmailMessageAdminService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
@Tag(name = "Messages", description = "Очередь писем для админки")
public class EmailMessageController {

    private final EmailMessageAdminService emailMessageAdminService;

    @Operation(summary = "Последние письма с фильтрами")
    @GetMapping
    public ResponseEntity<List<EmailMessageDTO>> list(@RequestParam(required = false) EmailMessageStatus status,
                                                      @RequestParam(required = false) EmailType type,
                                                      @RequestParam(required = false) String client,
                                                      @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(emailMessageAdminService.list(status, type, client, limit));
    }

    @Operation(summary = "Сколько писем в каждом статусе")
    @GetMapping("/stats")
    public ResponseEntity<QueueStatsDTO> stats() {
        return ResponseEntity.ok(emailMessageAdminService.stats());
    }

    @Operation(summary = "Письмо с HTML")
    @GetMapping("/{id}")
    public ResponseEntity<EmailMessageDetailsDTO> get(@PathVariable UUID id) {
        return ResponseEntity.ok(emailMessageAdminService.get(id));
    }

    @Operation(summary = "Вернуть неотправленное письмо в очередь")
    @PostMapping("/{id}/retry")
    public ResponseEntity<EmailMessageDTO> retry(@PathVariable UUID id) {
        return ResponseEntity.ok(emailMessageAdminService.retry(id));
    }
}
