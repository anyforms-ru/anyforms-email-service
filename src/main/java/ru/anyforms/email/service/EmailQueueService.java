package ru.anyforms.email.service;

import ru.anyforms.email.dto.BulkEmailRequest;
import ru.anyforms.email.dto.BulkEmailResponse;
import ru.anyforms.email.dto.SendEmailRequest;
import ru.anyforms.email.dto.SendEmailResponse;

public interface EmailQueueService {

    SendEmailResponse enqueue(String client, String idempotencyKey, SendEmailRequest request);

    /**
     * Ставит письмо по шаблону каждому адресу из списка — всё в одной транзакции: либо в очереди все письма,
     * либо ни одного. Повтор с тем же batchKey не дублирует письма уже поставленным адресам.
     */
    BulkEmailResponse enqueueBulk(String client, BulkEmailRequest request);
}
