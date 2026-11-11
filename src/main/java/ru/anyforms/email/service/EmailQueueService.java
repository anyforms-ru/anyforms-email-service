package ru.anyforms.email.service;

import ru.anyforms.email.dto.SendEmailRequest;
import ru.anyforms.email.dto.SendEmailResponse;

public interface EmailQueueService {

    SendEmailResponse enqueue(String client, String idempotencyKey, SendEmailRequest request);
}
